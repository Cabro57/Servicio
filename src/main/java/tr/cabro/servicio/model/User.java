package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
public class User {

    private int id;

    private String name;
    private String surname;
    private String email;
    private String password;

    private String businessName;
    private String phoneNumber;

    // YENİ EKLENDİ: Profil resminin dosya adı veya yolu (Örn: "avatar_1.png")
    private String profilePicture;

    private LocalDateTime createdAt;

    public User(String name, String surname, String email, String password, String businessName, String phoneNumber, String profilePicture) {
        this.name = name;
        this.surname = surname;
        this.email = email;
        this.password = password;
        this.businessName = businessName;
        this.phoneNumber = phoneNumber;
        this.profilePicture = profilePicture;
        this.createdAt = LocalDateTime.now();
    }

    public User() {
        this.name = "";
        this.surname = "";
        this.email = "";
        this.password = "";
        this.businessName = "";
        this.phoneNumber = "";
        this.profilePicture = "default_avatar.svg"; // Yeni eklenen kullanıcıların varsayılan bir resmi olsun
        this.createdAt = LocalDateTime.now();
    }

    public String getFullName() {
        return (name + " " + surname).trim();
    }
}