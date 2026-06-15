# SafeChat

Lekka, wielowątkowa aplikacja kliencko-serwerowa do komunikacji w czasie rzeczywistym. Zapewnia pełne bezpieczeństwo wiadomości dzięki szyfrowaniu hybrydowemu End-to-End (RSA + AES).

## Jak uruchomić

### Serwer

**Przez IDE:**

* uruchom klasę `com.safechat.server.ServerMain`

**Przez terminal:**

```bash
mvn clean compile
mvn exec:java "-Dexec.mainClass=com.safechat.server.ServerMain"
```

### Klient

**Przez IDE:**

* uruchom klasę `com.safechat.client.Launcher`

**Przez terminal:**

```bash
mvn javafx:run
```

## Jak uruchomić testy

```bash
mvn test
```

## Wymagania

* JDK 26
* Apache Maven 3.9 lub nowszy

Biblioteki JavaFX (wersja 22) są pobierane automatycznie przez Maven podczas pierwszego uruchomienia projektu. Nie jest wymagana ręczna instalacja JavaFX.
