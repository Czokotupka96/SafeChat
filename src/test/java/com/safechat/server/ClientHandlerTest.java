package com.safechat.server;

import com.safechat.shared.MessageDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

// testy jednostkowe dla klasy ClientHandler
// testujemy metode sendMessage() - czy poprawnie serializuje obiekty do strumienia
class ClientHandlerTest {

    @Test
    @DisplayName("sendMessage() zapisuje zserializowany MessageDTO do strumienia wyjsciowego")
    void testSendMessageWritesToStream() throws Exception {
        // tworzymy pare polaczonych socketow (server <-> client)
        ServerSocket serverSocket = new ServerSocket(0); // losowy port
        int port = serverSocket.getLocalPort();

        Socket clientSide = new Socket("localhost", port);
        Socket serverSide = serverSocket.accept();

        try {
            ConnectionManager cm = new ConnectionManager();
            ClientHandler handler = new ClientHandler(serverSide, cm);

            // musimy otworzyc strumienie w odpowiedniej kolejnosci
            // ClientHandler.run() normalnie otwiera strumienie, ale my testujemy sendMessage()
            // wiec recznie inicjalizujemy pole 'out' uzywajac refleksji
            ObjectOutputStream out = new ObjectOutputStream(serverSide.getOutputStream());
            out.flush();

            // ustawiamy pole 'out' w handlerze przez refleksje
            java.lang.reflect.Field outField = ClientHandler.class.getDeclaredField("out");
            outField.setAccessible(true);
            outField.set(handler, out);

            // przygotowanie strumienia wejsciowego po stronie klienta
            ObjectInputStream clientIn = new ObjectInputStream(clientSide.getInputStream());

            // wysylanie wiadomosci
            MessageDTO testMsg = new MessageDTO(
                    MessageDTO.MessageType.CHAT, "Server", "Alice", "Witaj!");
            handler.sendMessage(testMsg);

            // odczyt po stronie klienta
            MessageDTO received = (MessageDTO) clientIn.readObject();

            assertNotNull(received, "Odebrana wiadomosc nie powinna byc null");
            assertEquals("Server", received.getSender());
            assertEquals("Alice", received.getRecipient());
            assertEquals("Witaj!", received.getContent());
            assertEquals(MessageDTO.MessageType.CHAT, received.getType());
        } finally {
            clientSide.close();
            serverSide.close();
            serverSocket.close();
        }
    }
}
