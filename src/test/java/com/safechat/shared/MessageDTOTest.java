package com.safechat.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

// testy jednostkowe dla klasy MessageDTO
// sprawdza konstruktory, gettery, settery, toString oraz serializacje
class MessageDTOTest {

        // testy konstruktorow --------------------------------------

        @Test
        @DisplayName("Konstruktor bazowy (type, sender, recipient, content) ustawia pola poprawnie")
        void testBasicConstructorFields() {
                MessageDTO msg = new MessageDTO(
                                MessageDTO.MessageType.CHAT, "Alice", "Bob", "Hello!");

                assertEquals(MessageDTO.MessageType.CHAT, msg.getType());
                assertEquals("Alice", msg.getSender());
                assertEquals("Bob", msg.getRecipient());
                assertEquals("Hello!", msg.getContent());
                assertNull(msg.getEncryptedPayload(), "encryptedPayload powinien byc null");
                assertNull(msg.getPublicKey(), "publicKey powinien byc null");
        }

        @Test
        @DisplayName("Konstruktor szyfrowany (type, sender, recipient, byte[]) ustawia encryptedPayload")
        void testEncryptedConstructorFields() {
                byte[] payload = { 1, 2, 3, 4, 5 };
                MessageDTO msg = new MessageDTO(
                                MessageDTO.MessageType.CHAT, "Alice", "Bob", payload);

                assertEquals(MessageDTO.MessageType.CHAT, msg.getType());
                assertEquals("Alice", msg.getSender());
                assertEquals("Bob", msg.getRecipient());
                assertArrayEquals(payload, msg.getEncryptedPayload());
                assertNull(msg.getContent(), "content powinien byc null dla konstruktora szyfrowanego");
        }

        @Test
        @DisplayName("Konstruktor z kluczem publicznym (JOIN) ustawia publicKey")
        void testPublicKeyConstructorFields() {
                byte[] pubKey = { 10, 20, 30 };
                MessageDTO msg = new MessageDTO(
                                MessageDTO.MessageType.JOIN, "Alice", "ALL", "joined", pubKey);

                assertEquals(MessageDTO.MessageType.JOIN, msg.getType());
                assertEquals("Alice", msg.getSender());
                assertEquals("ALL", msg.getRecipient());
                assertEquals("joined", msg.getContent());
                assertArrayEquals(pubKey, msg.getPublicKey());
        }

        // testy timestamp --------------------------------------

        @Test
        @DisplayName("Timestamp jest ustawiony i ma sensowna wartosc (blisko aktualnego czasu)")
        void testTimestampIsSet() {
                long before = System.currentTimeMillis();
                MessageDTO msg = new MessageDTO(
                                MessageDTO.MessageType.CHAT, "Alice", "Bob", "test");
                long after = System.currentTimeMillis();

                assertTrue(msg.getTimestamp() >= before, "Timestamp powinien byc >= czas przed utworzeniem");
                assertTrue(msg.getTimestamp() <= after, "Timestamp powinien byc <= czas po utworzeniu");
        }

        // testy setterow --------------------------------------

        @Test
        @DisplayName("Settery nadpisuja pola encryptedPayload i publicKey")
        void testSettersOverrideFields() {
                MessageDTO msg = new MessageDTO(
                                MessageDTO.MessageType.CHAT, "Alice", "Bob", "test");

                assertNull(msg.getEncryptedPayload());
                assertNull(msg.getPublicKey());

                byte[] payload = { 99, 88, 77 };
                byte[] pubKey = { 11, 22, 33 };
                msg.setEncryptedPayload(payload);
                msg.setPublicKey(pubKey);

                assertArrayEquals(payload, msg.getEncryptedPayload());
                assertArrayEquals(pubKey, msg.getPublicKey());
        }

        // testy toString --------------------------------------

        @Test
        @DisplayName("toString dla wiadomosci zaszyfrowanej pokazuje [encrypted, N bytes]")
        void testToStringEncrypted() {
                byte[] payload = new byte[128];
                MessageDTO msg = new MessageDTO(
                                MessageDTO.MessageType.CHAT, "Alice", "Bob", payload);

                String result = msg.toString();
                assertTrue(result.contains("[encrypted, 128 bytes]"),
                                "Powinno zawierac informacje o zaszyfrowanej tresci, dostano: " + result);
                assertTrue(result.contains("Alice"), "Powinno zawierac nadawce");
                assertTrue(result.contains("Bob"), "Powinno zawierac odbiorce");
        }

        @Test
        @DisplayName("toString dla wiadomosci tekstowej pokazuje tresc")
        void testToStringPlaintext() {
                MessageDTO msg = new MessageDTO(
                                MessageDTO.MessageType.CHAT, "Alice", "Bob", "Hello world!");

                String result = msg.toString();
                assertTrue(result.contains("Hello world!"),
                                "Powinno zawierac tresc wiadomosci, dostano: " + result);
        }

        // testy serializacji --------------------------------------

        @Test
        @DisplayName("Serializacja i deserializacja zachowuje wszystkie pola (round-trip)")
        void testSerializationRoundTrip() throws Exception {
                byte[] pubKey = { 1, 2, 3, 4, 5 };
                MessageDTO original = new MessageDTO(
                                MessageDTO.MessageType.JOIN, "Alice", "ALL", "Hello", pubKey);
                original.setEncryptedPayload(new byte[] { 10, 20, 30 });

                // serializacja do bajtow
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(original);
                oos.flush();
                byte[] serialized = bos.toByteArray();

                // deserializacja z bajtow
                ByteArrayInputStream bis = new ByteArrayInputStream(serialized);
                ObjectInputStream ois = new ObjectInputStream(bis);
                MessageDTO restored = (MessageDTO) ois.readObject();

                // weryfikacja pol
                assertEquals(original.getType(), restored.getType());
                assertEquals(original.getSender(), restored.getSender());
                assertEquals(original.getRecipient(), restored.getRecipient());
                assertEquals(original.getContent(), restored.getContent());
                assertEquals(original.getTimestamp(), restored.getTimestamp());
                assertArrayEquals(original.getPublicKey(), restored.getPublicKey());
                assertArrayEquals(original.getEncryptedPayload(), restored.getEncryptedPayload());
        }

        // testy enum MessageType --------------------------------------

        @Test
        @DisplayName("Kazdy typ wiadomosci (MessageType) moze byc uzyty w konstruktorze")
        void testAllMessageTypes() {
                for (MessageDTO.MessageType type : MessageDTO.MessageType.values()) {
                        MessageDTO msg = new MessageDTO(type, "sender", "recipient", "content");
                        assertEquals(type, msg.getType(),
                                        "Typ " + type + " powinien byc poprawnie zapisany i odczytany");
                }
        }

        // testy chunkowania --------------------------------------

        @Test
        @DisplayName("Konstruktor bazowy ustawia domyslne wartosci chunkowania (totalChunks=1, chunkIndex=0, messageId=null)")
        void testDefaultChunkFieldsBasicConstructor() {
                MessageDTO msg = new MessageDTO(
                                MessageDTO.MessageType.CHAT, "Alice", "Bob", "Hello!");

                assertEquals(1, msg.getTotalChunks(), "totalChunks domyslnie powinno byc 1");
                assertEquals(0, msg.getChunkIndex(), "chunkIndex domyslnie powinno byc 0");
                assertNull(msg.getMessageId(), "messageId domyslnie powinno byc null");
        }

        @Test
        @DisplayName("Konstruktor szyfrowany ustawia domyslne wartosci chunkowania")
        void testDefaultChunkFieldsEncryptedConstructor() {
                byte[] payload = { 1, 2, 3 };
                MessageDTO msg = new MessageDTO(
                                MessageDTO.MessageType.CHAT, "Alice", "Bob", payload);

                assertEquals(1, msg.getTotalChunks());
                assertEquals(0, msg.getChunkIndex());
                assertNull(msg.getMessageId());
        }

        @Test
        @DisplayName("Konstruktor z kluczem publicznym ustawia domyslne wartosci chunkowania")
        void testDefaultChunkFieldsPublicKeyConstructor() {
                byte[] pubKey = { 10, 20, 30 };
                MessageDTO msg = new MessageDTO(
                                MessageDTO.MessageType.JOIN, "Alice", "ALL", "joined", pubKey);

                assertEquals(1, msg.getTotalChunks());
                assertEquals(0, msg.getChunkIndex());
                assertNull(msg.getMessageId());
        }

        @Test
        @DisplayName("Settery chunkowania poprawnie ustawiaja pola messageId, chunkIndex, totalChunks")
        void testChunkSettersAndGetters() {
                MessageDTO msg = new MessageDTO(
                                MessageDTO.MessageType.CHAT, "Alice", "Bob", "Chunk 1");

                msg.setMessageId("abc-123");
                msg.setChunkIndex(2);
                msg.setTotalChunks(5);

                assertEquals("abc-123", msg.getMessageId());
                assertEquals(2, msg.getChunkIndex());
                assertEquals(5, msg.getTotalChunks());
        }

        @Test
        @DisplayName("toString zawiera informacje o chunku gdy totalChunks > 1")
        void testToStringWithChunkInfo() {
                MessageDTO msg = new MessageDTO(
                                MessageDTO.MessageType.CHAT, "Alice", "Bob", "Part of message");
                msg.setMessageId("test-id");
                msg.setChunkIndex(1);
                msg.setTotalChunks(3);

                String result = msg.toString();
                assertTrue(result.contains("[chunk 2/3]"),
                                "toString powinno zawierac [chunk 2/3], dostano: " + result);
        }

        @Test
        @DisplayName("toString nie zawiera informacji o chunku gdy totalChunks == 1")
        void testToStringWithoutChunkInfoWhenSingleChunk() {
                MessageDTO msg = new MessageDTO(
                                MessageDTO.MessageType.CHAT, "Alice", "Bob", "Normal message");

                String result = msg.toString();
                assertFalse(result.contains("chunk"),
                                "toString nie powinno zawierac 'chunk' dla pojedynczej wiadomosci, dostano: " + result);
        }

        @Test
        @DisplayName("Serializacja zachowuje pola chunkowania (round-trip)")
        void testSerializationRoundTripWithChunkFields() throws Exception {
                MessageDTO original = new MessageDTO(
                                MessageDTO.MessageType.CHAT, "Alice", "Bob", "Chunk content");
                original.setMessageId("uuid-test-123");
                original.setChunkIndex(3);
                original.setTotalChunks(7);

                // serializacja
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(original);
                oos.flush();

                // deserializacja
                ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
                ObjectInputStream ois = new ObjectInputStream(bis);
                MessageDTO restored = (MessageDTO) ois.readObject();

                assertEquals(original.getMessageId(), restored.getMessageId());
                assertEquals(original.getChunkIndex(), restored.getChunkIndex());
                assertEquals(original.getTotalChunks(), restored.getTotalChunks());
                assertEquals(original.getContent(), restored.getContent());
                assertEquals(original.getSender(), restored.getSender());
        }

        // testy stalych --------------------------------------

        @Test
        @DisplayName("MAX_CHUNK_SIZE i MAX_TOTAL_CHUNKS maja poprawne wartosci")
        void testChunkConstants() {
                assertEquals(10000, MessageDTO.MAX_CHUNK_SIZE, "MAX_CHUNK_SIZE powinno byc 10000");
                assertEquals(10, MessageDTO.MAX_TOTAL_CHUNKS, "MAX_TOTAL_CHUNKS powinno byc 10");
                assertTrue(MessageDTO.MAX_CHUNK_SIZE > 0, "MAX_CHUNK_SIZE musi byc dodatnie");
                assertTrue(MessageDTO.MAX_TOTAL_CHUNKS > 0, "MAX_TOTAL_CHUNKS musi byc dodatnie");
        }
}
