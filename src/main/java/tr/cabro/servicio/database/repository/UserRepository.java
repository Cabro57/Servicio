package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.User;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(User.class)
public interface UserRepository {

    @SqlUpdate("INSERT INTO users (name, surname, email, password, profile_picture, created_at) " +
            "VALUES (:name, :surname, :email, :password, :profilePicture, :createdAt)")
    @GetGeneratedKeys
    int insert(@BindBean User user);

    // İşletme kolonları (business_name, phone_number, address, logo_path) V28'den beri yazılmaz.
    @SqlUpdate("UPDATE users SET name=:name, surname=:surname, email=:email, password=:password, " +
            "profile_picture=:profilePicture WHERE id=:id")
    void update(@BindBean User user);

    /** Yalnızca kurtarma kodu özeti; genel {@link #update} bu kolona dokunmaz. */
    @SqlUpdate("UPDATE users SET recovery_code = :hash WHERE id = :id")
    void setRecoveryCode(@Bind("id") Long id, @Bind("hash") String hash);

    /** Yalnızca PIN özeti (kurtarma akışı). */
    @SqlUpdate("UPDATE users SET password = :hash WHERE id = :id")
    void setPassword(@Bind("id") Long id, @Bind("hash") String hash);

    @SqlUpdate("DELETE FROM users WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlQuery("SELECT * FROM users WHERE email = :email")
    Optional<User> findByEmail(@Bind("email") String email);

    @SqlQuery("SELECT * FROM users WHERE id = :id")
    Optional<User> findById(@Bind("id") Long id);

    @SqlQuery("SELECT * FROM users ORDER BY name ASC")
    List<User> findAll();
}