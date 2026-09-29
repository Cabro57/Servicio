package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
public class User {

    private Long id;

    private String name;
    private String surname;
    private String email;
    private String password;

    // İşletme bilgileri (ad, telefon, adres, logo…) V28'den beri Business / business_profile'da.

    // Profil resminin "profiles/" alt klasöründeki dosya adı
    private String profilePicture;

    private LocalDateTime createdAt;

    public User(String name, String surname, String email, String password, String profilePicture) {
        this.name = name;
        this.surname = surname;
        this.email = email;
        this.password = password;
        this.profilePicture = profilePicture;
        this.createdAt = LocalDateTime.now();
    }

    public User() {
        this.name = "";
        this.surname = "";
        this.email = "";
        this.password = "";
        this.profilePicture = "default_avatar.svg"; // Yeni eklenen kullanıcıların varsayılan bir resmi olsun
        this.createdAt = LocalDateTime.now();
    }

    public String getFullName() {
        return (name + " " + surname).trim();
    }
}