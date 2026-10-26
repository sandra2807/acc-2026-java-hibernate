package bootcamp.hibernate_practical.service;

import bootcamp.hibernate_practical.entity.Book;
import bootcamp.hibernate_practical.repository.BookRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class BookService {
    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public Book createBook(Book request) {
        // Copy only input fields so a client-supplied ID cannot update a book.
        Book book = new Book(request.getTitle(), request.getAuthor(), request.getPublicationYear());
        return bookRepository.save(book);
    }

    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }

    public Book getBookById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Book with ID " + id + " was not found"));
    }

    public void deleteBook(Long id) {
        Book book = getBookById(id);
        bookRepository.delete(book);
    }

    public List<Book> findByAuthor(String author) {
        if (author.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Author must not be blank");
        }
        return bookRepository.findByAuthorContainingIgnoreCase(author.trim());
    }

    public List<Book> findPublishedAfter(Integer year) {
        return bookRepository.findByPublicationYearGreaterThan(year);
    }

    public long countBooks() {
        return bookRepository.count();
    }
}
