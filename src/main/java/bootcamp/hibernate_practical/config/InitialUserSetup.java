package bootcamp.hibernate_practical.config;

import bootcamp.hibernate_practical.entity.AppUser;
import bootcamp.hibernate_practical.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class InitialUserSetup implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InitialUserSetup.class);
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Environment environment;

    public InitialUserSetup(AppUserRepository users, PasswordEncoder encoder, Environment environment) {
        this.users = users;
        this.encoder = encoder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = environment.getRequiredProperty("app.security.user.username");
        String admin = environment.getRequiredProperty("app.security.admin.username");
        if (username.isBlank() || admin.isBlank() || username.length() > 100
                || admin.length() > 100 || username.equals(admin)) {
            throw new IllegalStateException("Initial USER and ADMIN usernames must be distinct, nonblank, and at most 100 characters");
        }
        createIfMissing(username, "app.security.user.password", AppUser.Role.USER);
        createIfMissing(admin, "app.security.admin.password", AppUser.Role.ADMIN);
    }

    private void createIfMissing(String username, String passwordProperty, AppUser.Role role) {
        if (users.findByUsername(username).isPresent()) {
            return;
        }
        String password = environment.getProperty(passwordProperty, "");
        if (password.isBlank()) {
            log.warn("Initial {} account was not created: configure its password in the environment", role);
            return;
        }
        users.save(new AppUser(username, encoder.encode(password), role));
    }
}
