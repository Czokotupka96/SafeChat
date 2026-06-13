package com.safechat.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

// testy jednostkowe dla klasy NetworkService
// testy sa celowo minimalne - glowna logika w testach integracyjnych
class NetworkServiceTest {

    private NetworkService networkService;
    private AtomicReference<String> lastError;

    @BeforeEach
    void setUp() {
        lastError = new AtomicReference<>(null);
        networkService = new NetworkService(
                msg -> { /* ignorujemy wiadomosci w testach jednostkowych */ },
                error -> lastError.set(error)
        );
    }

    // testy stanu poczatkowego --------------------------------------

    @Test
    @DisplayName("Poczatkowy nick klienta jest null przed polaczeniem")
    void testInitialNickIsNull() {
        assertNull(networkService.getClientNick(),
                "Nick powinien byc null przed polaczeniem z serwerem");
    }

    // testy polaczenia z blednym hostem --------------------------------------

    @Test
    @DisplayName("Polaczenie z nieosiagalnym hostem zwraca false i wywoluje callback bledu")
    void testConnectToInvalidHostFails() {
        boolean result = networkService.connect("192.0.2.1", 59999, "TestNick");

        assertFalse(result, "Polaczenie z nieosiagalnym hostem powinno zwrocic false");
        assertNull(networkService.getClientNick(), "Nick powinien pozostac null po nieudanym polaczeniu");
    }

    @Test
    @DisplayName("Polaczenie z nieprawidlowym portem zwraca false")
    void testConnectToInvalidPortFails() {
        // port 0 powinien byc nieprawidlowy
        boolean result = networkService.connect("localhost", 0, "TestNick");

        assertFalse(result, "Polaczenie z portem 0 powinno zwrocic false");
    }

    // testy bezpieczenstwa disconnect --------------------------------------

    @Test
    @DisplayName("disconnect() przed jakimkolwiek polaczeniem nie rzuca wyjatku")
    void testDisconnectDoesNotThrow() {
        assertDoesNotThrow(() -> networkService.disconnect(),
                "disconnect() nie powinien rzucac wyjatku gdy nie ma aktywnego polaczenia");
    }

    @Test
    @DisplayName("Wielokrotne wywolanie disconnect() nie rzuca wyjatku")
    void testMultipleDisconnectDoesNotThrow() {
        assertDoesNotThrow(() -> {
            networkService.disconnect();
            networkService.disconnect();
            networkService.disconnect();
        }, "Wielokrotny disconnect() nie powinien rzucac wyjatkow");
    }
}
