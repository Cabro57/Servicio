package tr.cabro.servicio.util;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.settings.AppSettings;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Uygulamanın hangi donanımda çalıştığını ve o donanımın ne kadarını kullandığını ölçer
 * (Ayarlar &gt; Sistem &gt; Kaynak Kullanımı).
 * <ul>
 *   <li>{@link #hardware()} — makine kimliği; işlemci adı için bir kez dış komut çalışabilir, EDT dışında çağrılmalı.</li>
 *   <li>{@link #sample()} — anlık ölçüm (MXBean okumaları, mikro saniyeler); saniyede bir çağrılabilir.</li>
 *   <li>{@link #disk()} — klasör boyutları; dosya ağacı gezildiği için EDT dışında, seyrek çağrılmalı.</li>
 * </ul>
 * Hiçbir metot hata fırlatmaz: ölçülemeyen değer -1 ya da null döner, arayüz "—" yazar.
 */
public final class ResourceMonitor {

    private ResourceMonitor() {}

    // ------------------------------------------------------------------ donanım

    public record Hardware(String cpuName, int cores, long physicalTotal, String os, String javaVersion,
                           String javaVendor, String javaHome, List<Screen> screens, String pipeline) {}

    public record Screen(int width, int height, double scale, int refreshRate, boolean primary) {}

    private static volatile Hardware cachedHardware;

    public static Hardware hardware() {
        Hardware h = cachedHardware;
        if (h != null) return h;
        com.sun.management.OperatingSystemMXBean os = osBean();
        h = new Hardware(
                cpuName(),
                Runtime.getRuntime().availableProcessors(),
                os != null ? os.getTotalMemorySize() : -1,
                System.getProperty("os.name", "") + " " + System.getProperty("os.version", "") + " (" + System.getProperty("os.arch", "") + ")",
                System.getProperty("java.runtime.version", System.getProperty("java.version", "")),
                System.getProperty("java.vendor", ""),
                System.getProperty("java.home", ""),
                screens(),
                pipeline());
        cachedHardware = h;
        return h;
    }

    /** İşlemcinin pazarlama adı ("Intel(R) Core(TM) i5-10400"); okunamazsa işletim sisteminin tanımı. */
    private static String cpuName() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        try {
            if (windows) {
                String out = run("reg", "query", "HKLM\\HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0", "/v", "ProcessorNameString");
                for (String line : out.split("\\R")) {
                    int i = line.indexOf("REG_SZ");
                    if (i >= 0) return line.substring(i + 6).trim();
                }
            } else {
                Path info = Path.of("/proc/cpuinfo");
                if (Files.isReadable(info)) {
                    for (String line : Files.readAllLines(info)) {
                        if (line.startsWith("model name")) return line.substring(line.indexOf(':') + 1).trim();
                    }
                }
            }
        } catch (Exception e) {
            Servicio.getLogger().debug("İşlemci adı okunamadı: {}", e.getMessage());
        }
        String id = System.getenv("PROCESSOR_IDENTIFIER");
        return id != null && !id.isBlank() ? id : System.getProperty("os.arch", "Bilinmeyen işlemci");
    }

    private static String run(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly();
        return sb.toString();
    }

    private static List<Screen> screens() {
        List<Screen> list = new ArrayList<>();
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice primary = ge.getDefaultScreenDevice();
            for (GraphicsDevice d : ge.getScreenDevices()) {
                DisplayMode m = d.getDisplayMode();
                double scale = d.getDefaultConfiguration().getDefaultTransform().getScaleX();
                list.add(new Screen(m.getWidth(), m.getHeight(), scale,
                        m.getRefreshRate() == DisplayMode.REFRESH_RATE_UNKNOWN ? -1 : m.getRefreshRate(), d == primary));
            }
        } catch (Exception | Error e) {
            // Başsız ortam (test): ekran yok.
        }
        return list;
    }

    /** Java2D'nin çizim yolu: ekran kartı hızlandırması açık mı. */
    private static String pipeline() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (Boolean.parseBoolean(System.getProperty("sun.java2d.opengl"))) return "OpenGL (ekran kartı)";
        if (windows) {
            return "false".equalsIgnoreCase(System.getProperty("sun.java2d.d3d"))
                    ? "Yazılım (ekran kartı kapalı)" : "Direct3D (ekran kartı)";
        }
        return "XRender";
    }

    // ------------------------------------------------------------------ anlık ölçüm

    /**
     * @param processCpu uygulamanın işlemci payı 0..1 (-1: ölçülemedi)
     * @param systemCpu  tüm sistemin işlemci yükü 0..1 (-1: ölçülemedi)
     */
    public record Sample(double processCpu, double systemCpu,
                         long heapUsed, long heapCommitted, long heapMax, long nonHeapUsed,
                         long physicalTotal, long physicalFree,
                         int threads, int peakThreads, long gcCount, long gcTimeMs, long uptimeMs,
                         int poolActive, int poolIdle, int poolTotal) {}

    public static Sample sample() {
        com.sun.management.OperatingSystemMXBean os = osBean();
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        long gcCount = 0, gcTime = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += Math.max(0, gc.getCollectionCount());
            gcTime += Math.max(0, gc.getCollectionTime());
        }
        int[] pool = DatabaseManager.poolStats();
        var heap = mem.getHeapMemoryUsage();
        return new Sample(
                os != null ? os.getProcessCpuLoad() : -1,
                os != null ? os.getCpuLoad() : -1,
                heap.getUsed(), heap.getCommitted(), heap.getMax(), mem.getNonHeapMemoryUsage().getUsed(),
                os != null ? os.getTotalMemorySize() : -1, os != null ? os.getFreeMemorySize() : -1,
                threads.getThreadCount(), threads.getPeakThreadCount(), gcCount, gcTime,
                ManagementFactory.getRuntimeMXBean().getUptime(),
                pool[0], pool[1], pool[2]);
    }

    /** Çöp toplayıcıyı çalıştırır; boşalan bellek (bayt) döner. */
    public static long collectGarbage() {
        long before = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        System.gc();
        long after = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        return Math.max(0, before - after);
    }

    private static com.sun.management.OperatingSystemMXBean osBean() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean b ? b : null;
    }

    // ------------------------------------------------------------------ disk

    public record Disk(String dataPath, String volume, long volumeTotal, long volumeFree,
                       long database, long backups, long logs, long otherData,
                       String installPath, long install) {
        public long dataTotal() {
            return database + backups + logs + otherData;
        }
    }

    public static Disk disk() {
        File data = dataFolder();
        File db = new File(data, "database");
        File logs = new File(data, "logs");
        File backups = AppSettings.getBackupDir();
        long dbSize = size(db);
        long logSize = size(logs);
        long backupSize = size(backups);
        // Yedek klasörü veri klasörünün içindeyse "diğer" hesabından düşülür (iki kez sayılmasın).
        boolean backupsInside = backups != null && backups.toPath().toAbsolutePath().startsWith(data.toPath().toAbsolutePath());
        long other = Math.max(0, size(data) - dbSize - logSize - (backupsInside ? backupSize : 0));

        File install = installFolder();
        Path root = data.toPath().toAbsolutePath().getRoot();
        File volume = root != null ? root.toFile() : data;
        return new Disk(data.getAbsolutePath(), volume.getAbsolutePath(), volume.getTotalSpace(), volume.getUsableSpace(),
                dbSize, backupSize, logSize, other,
                install != null ? install.getAbsolutePath() : null, install != null ? size(install) : -1);
    }

    public static File dataFolder() {
        try {
            return Servicio.getInstance().getDataFolder();
        } catch (Exception e) {
            String base = System.getProperty("servicio.baseDir");
            return new File(base != null ? new File(base) : DataDirResolver.resolveBaseFolder(), ".servicio");
        }
    }

    /** Uygulama dosyalarının klasörü (servicio.jar'ın yanı; jpackage kurulumunda "app"). */
    private static File installFolder() {
        try {
            File jar = new File(Servicio.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            // Geliştirme ortamında target/classes: ölçmek anlamsız.
            return jar.isFile() ? jar.getParentFile() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static long size(File dir) {
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            return walk.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        } catch (Exception e) {
            return 0;
        }
    }
}
