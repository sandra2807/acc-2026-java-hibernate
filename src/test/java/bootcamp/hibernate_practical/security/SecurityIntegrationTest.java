package bootcamp.hibernate_practical.security;

import bootcamp.hibernate_practical.config.InitialUserSetup;
import bootcamp.hibernate_practical.entity.AppUser;
import bootcamp.hibernate_practical.entity.Book;
import bootcamp.hibernate_practical.repository.AppUserRepository;
import bootcamp.hibernate_practical.repository.BookRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {
    private static final String BOOK_JSON = """
            {"title":"Dune","author":"Frank Herbert","publicationYear":1965}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private BookRepository books;
    @Autowired private AppUserRepository users;
    @Autowired private PasswordEncoder encoder;
    private Book book;

    @BeforeEach
    void prepareInventory() {
        books.deleteAll();
        book = books.save(new Book("Dune", "Frank Herbert", 1965));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/books", "/books/1", "/books/count", "/books/search/author?author=Herbert",
            "/books/search/year?after=1960", "/books/external/search?title=Dune"})
    void anonymousApiReadsReturn401(String path) throws Exception {
        mvc.perform(get(path).accept(MediaType.TEXT_HTML)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void anonymousApiWritesReturn401(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/books")).andExpect(status().isUnauthorized());
        mvc.perform(request(HttpMethod.valueOf(method), "/books").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/h2-console/", "/another-page"})
    void anonymousBrowserRequestsRedirectToLogin(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void defaultLoginAndLogoutFormsIncludeCsrf() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("_csrf")));
        mvc.perform(get("/logout").session(login("test-user", "test-user-password")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("_csrf")));
    }

    @Test
    void databaseUserCanReadAllExistingEndpointsAndHome() throws Exception {
        MockHttpSession session = login("test-user", "test-user-password");
        mvc.perform(get("/").session(session)).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Library Inventory")));
        mvc.perform(get("/books").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Dune"));
        mvc.perform(get("/books/{id}", book.getId()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("title").value("Dune"));
        mvc.perform(get("/books/search/author").param("author", "herBERT").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Dune"));
        mvc.perform(get("/books/search/year").param("after", "1960").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Dune"));
        mvc.perform(get("/books/count").session(session)).andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void userCannotWriteEvenWithValidCsrf(String method) throws Exception {
        String path = method.equals("POST") ? "/books" : "/books/" + book.getId();
        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .session(login("test-user", "test-user-password"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
                .andExpect(status().isForbidden());
        assertThat(books.count()).isEqualTo(1);
    }

    @Test
    void adminCanReadCreateAndDeleteWithCsrf() throws Exception {
        MockHttpSession session = login("test-admin", "test-admin-password");
        mvc.perform(get("/books").session(session)).andExpect(status().isOk());
        mvc.perform(post("/books").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
                .andExpect(status().isCreated()).andExpect(jsonPath("title").value("Dune"));
        assertThat(books.count()).isEqualTo(2);
        mvc.perform(delete("/books/{id}", book.getId()).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(books.existsById(book.getId())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "PATCH"})
    void unsupportedUpdateMethodsRemainUnsupportedForAdmin(String method) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), "/books/" + book.getId())
                        .session(login("test-admin", "test-admin-password")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
                .andExpect(status().isMethodNotAllowed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void adminWritesRequireValidCsrf(String method) throws Exception {
        MockHttpSession session = login("test-admin", "test-admin-password");
        String path = method.equals("POST") ? "/books" : "/books/" + book.getId();
        mvc.perform(request(HttpMethod.valueOf(method), path).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
                .andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.valueOf(method), path).session(session).with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
                .andExpect(status().isForbidden());
        assertThat(books.count()).isEqualTo(1);
    }

    @Test
    void passwordsAreBcryptAndInitializerDoesNotOverwriteThem() throws Exception {
        for (String role : new String[]{"user", "admin"}) {
            AppUser account = users.findByUsername("test-" + role).orElseThrow();
            assertThat(account.getPassword()).startsWith("$2").hasSize(60);
            assertThat(encoder.matches("test-" + role + "-password", account.getPassword())).isTrue();
            assertThat(account.getRole().name()).isEqualTo(role.toUpperCase());
        }
        String original = users.findByUsername("test-user").orElseThrow().getPassword();
        MockEnvironment changed = new MockEnvironment()
                .withProperty("app.security.user.username", "test-user")
                .withProperty("app.security.admin.username", "test-admin")
                .withProperty("app.security.user.password", "changed-test-password");
        new InitialUserSetup(users, encoder, changed).run(new DefaultApplicationArguments());
        assertThat(users.findByUsername("test-user").orElseThrow().getPassword()).isEqualTo(original);
        login("test-user", "test-user-password");
    }

    @Test
    void initializerSkipsMissingPasswordsAndRejectsDuplicateUsernames() {
        MockEnvironment missing = new MockEnvironment()
                .withProperty("app.security.user.username", "not-created-user")
                .withProperty("app.security.admin.username", "not-created-admin");
        new InitialUserSetup(users, encoder, missing).run(new DefaultApplicationArguments());
        assertThat(users.findByUsername("not-created-user")).isEmpty();
        assertThat(users.findByUsername("not-created-admin")).isEmpty();
        missing.setProperty("app.security.admin.username", "not-created-user");
        assertThatThrownBy(() -> new InitialUserSetup(users, encoder, missing)
                .run(new DefaultApplicationArguments())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrongPasswordFailsAndLoginWithoutCsrfFails() throws Exception {
        mvc.perform(formLogin().user("test-user").password("wrong-test-password"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").param("username", "test-user").param("password", "test-user-password"))
                .andExpect(status().isForbidden()).andExpect(unauthenticated());
    }

    @Test
    void realSessionTokensWorkForLoginApiAndLogout() throws Exception {
        var initial = mvc.perform(get("/csrf")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        MockHttpSession session = (MockHttpSession) initial.getRequest().getSession(false);
        String loginToken = JsonPath.read(initial.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/login").session(session).param("username", "test-admin")
                        .param("password", "test-admin-password").param("_csrf", loginToken))
                .andExpect(authenticated().withRoles("ADMIN")).andExpect(redirectedUrl("/"));
        mvc.perform(post("/books").session(session).header("X-CSRF-TOKEN", loginToken)
                        .contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
                .andExpect(status().isForbidden());
        var fresh = mvc.perform(get("/csrf").session(session)).andReturn();
        String apiToken = JsonPath.read(fresh.getResponse().getContentAsString(), "$.token");
        mvc.perform(post("/books").session(session).header("X-CSRF-TOKEN", apiToken)
                        .contentType(MediaType.APPLICATION_JSON).content(BOOK_JSON))
                .andExpect(status().isCreated());
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/books").session(session)).andExpect(status().isOk());
        mvc.perform(post("/logout").session(session).header("X-CSRF-TOKEN", apiToken))
                .andExpect(redirectedUrl("/login?logout"));
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/books")).andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotAccessH2Console() throws Exception {
        mvc.perform(get("/h2-console/").session(login("test-user", "test-user-password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingExternalKeyReturnsSafeMessageAndLocalInventoryStillWorks() throws Exception {
        MockHttpSession session = login("test-user", "test-user-password");
        mvc.perform(get("/books/external/search").param("title", "Dune").session(session))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("error").value(
                        "External book search is not configured. Local inventory is still available."));
        mvc.perform(get("/books").session(session)).andExpect(status().isOk());
    }

    @Test
    void existingValidationAndNotFoundResponsesArePreserved() throws Exception {
        MockHttpSession session = login("test-admin", "test-admin-password");
        mvc.perform(post("/books").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error").exists());
        mvc.perform(get("/books/999999").session(session)).andExpect(status().isNotFound());
        mvc.perform(delete("/books/999999").session(session).with(csrf()))
                .andExpect(status().isNotFound());
    }

    private MockHttpSession login(String username, String password) throws Exception {
        var result = mvc.perform(formLogin().user(username).password(password))
                .andExpect(status().is3xxRedirection()).andExpect(authenticated().withUsername(username))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
