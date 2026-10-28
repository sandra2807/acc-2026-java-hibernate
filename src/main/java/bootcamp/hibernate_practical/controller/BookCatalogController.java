package bootcamp.hibernate_practical.controller;

import bootcamp.hibernate_practical.service.BookCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
public class BookCatalogController {
    private final BookCatalogService catalog;

    public BookCatalogController(BookCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/books/external/search")
    public Map<String, Object> search(@RequestParam String title) {
        return catalog.search(title);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("error", exception.getReason()));
    }
}
