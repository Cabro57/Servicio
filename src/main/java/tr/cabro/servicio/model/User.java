package tr.cabro.servicio.model;

public class User {

    private String userName;
    private String mail;
    private Role role;
    private String businessName; // Yeni eklendi
    private String phoneNumber;  // Yeni eklendi

    public User(String userName, String mail, Role role, String businessName, String phoneNumber) {
        this.userName = userName;
        this.mail = mail;
        this.role = role;
        this.businessName = businessName;
        this.phoneNumber = phoneNumber;
    }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getMail() { return mail; }
    public void setMail(String mail) { this.mail = mail; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public enum Role {
        ADMIN, STAFF;

        @Override
        public String toString() {
            if (this == ADMIN) {
                return "Admin";
            }
            return "Staff";
        }
    }
}