package bootcamp.hibernate_practical.service;

import bootcamp.hibernate_practical.entity.Book;
import bootcamp.hibernate_practical.repository.BookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceTest {
    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private BookService bookService;

    @Test
    void createBookSavesNewBookWithoutClientId() {
        Book request = new Book("Harry Potter", "J. K. Rowling", 2001);
        request.setId(99L);
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            assertNull(book.getId());
            book.setId(1L);
            return book;
        });

        Book result = bookService.createBook(request);

        assertEquals(1L, result.getId());
        assertEquals("Harry Potter", result.getTitle());
        assertEquals("J. K. Rowling", result.getAuthor());
        assertEquals(2001, result.getPublicationYear());
        verify(bookRepository).save(any(Book.class));
    }

    @Test
    void getAllBooksReturnsRepositoryResults() {
        List<Book> books = List.of(new Book("Dune", "Frank Herbert", 1965));
        when(bookRepository.findAll()).thenReturn(books);

        assertEquals(books, bookService.getAllBooks());
        verify(bookRepository).findAll();
    }

    @Test
    void getBookByIdReturnsBookOrThrowsNotFound() {
        Book book = new Book("Dune", "Frank Herbert", 1965);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.findById(2L)).thenReturn(Optional.empty());

        assertSame(book, bookService.getBookById(1L));
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookService.getBookById(2L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void deleteBookDeletesExistingBookAndRejectsMissingBook() {
        Book book = new Book("Dune", "Frank Herbert", 1965);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.findById(2L)).thenReturn(Optional.empty());

        bookService.deleteBook(1L);

        verify(bookRepository).delete(book);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookService.deleteBook(2L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(bookRepository, never()).deleteById(2L);
    }

    @Test
    void findByAuthorTrimsQueryAndRejectsBlankAuthor() {
        List<Book> books = List.of(new Book("Harry Potter", "J. K. Rowling", 2001));
        when(bookRepository.findByAuthorContainingIgnoreCase("rowLING")).thenReturn(books);

        assertEquals(books, bookService.findByAuthor("  rowLING  "));
        verify(bookRepository).findByAuthorContainingIgnoreCase("rowLING");
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> bookService.findByAuthor("  "));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(bookRepository, never()).findByAuthorContainingIgnoreCase("");
    }

    @Test
    void findPublishedAfterUsesExclusiveYearQuery() {
        List<Book> books = List.of(new Book("Harry Potter", "J. K. Rowling", 2001));
        when(bookRepository.findByPublicationYearGreaterThan(2000)).thenReturn(books);

        assertEquals(books, bookService.findPublishedAfter(2000));
        verify(bookRepository).findByPublicationYearGreaterThan(2000);
    }

    @Test
    void countBooksReturnsRepositoryCount() {
        when(bookRepository.count()).thenReturn(3L);

        assertEquals(3L, bookService.countBooks());
        verify(bookRepository).count();
    }
}
