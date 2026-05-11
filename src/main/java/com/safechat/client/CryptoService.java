package com.safechat.client;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serwis kryptograficzny klienta SafeChat.
 * Odpowiada za generowanie kluczy RSA/AES, szyfrowanie/deszyfrowanie wiadomosci
 * oraz zarzadzanie kluczami publicznymi innych uzytkownikow i kluczami
 * sesyjnymi AES.
 *
 * Model hybrydowy: RSA 2048-bit (wymiana kluczy) + AES 256-bit GCM (szyfrowanie
 * wiadomosci)
 */
public class CryptoService {

    // stale kryptograficzne 
    private static final String RSA_ALGORITHM = "RSA";
    private static final String RSA_CIPHER_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int RSA_KEY_SIZE = 2048;

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_LENGTH = 12; // 12 bajtow IV
    private static final int GCM_TAG_LENGTH = 128; // 128-bitowy tag uwierzytelniajacy

    // para kluczy RSA tego klienta
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    // mapy przechowujace klucze innych uzytkownikow
    // klucze publiczne RSA innych uzytkownikow (nick -> PublicKey)
    private final Map<String, PublicKey> publicKeyStore = new ConcurrentHashMap<>();
    // klucze sesyjne AES dla konwersacji prywatnych (nick -> SecretKey)
    private final Map<String, SecretKey> aesKeyStore = new ConcurrentHashMap<>();

    // Konstruktor generujacy pare kluczy RSA 2048-bit dla tego klienta.
    public CryptoService() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            keyPairGenerator.initialize(RSA_KEY_SIZE);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();
            System.out.println("[CRYPTO] RSA key pair generated (2048-bit)");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Fatal: RSA algorithm not available", e);
        }
    }

    // RSA - klucze publiczne
    // Zwraca klucz publiczny tego klienta jako tablice bajtow
    public byte[] getPublicKeyBytes() {
        return publicKey.getEncoded();
    }

    // Zapisuje klucz publiczny RSA innego uzytkownika.
    public void storePublicKey(String nick, byte[] publicKeyBytes) {
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            PublicKey restoredKey = keyFactory.generatePublic(keySpec);
            publicKeyStore.put(nick, restoredKey);
            System.out.println("[CRYPTO] Stored public key for user: " + nick);
        } catch (Exception e) {
            System.err.println("[CRYPTO] Error storing public key for " + nick + ": " + e.getMessage());
        }
    }

    // Sprawdza czy mamy klucz publiczny RSA danego uzytkownika.
    public boolean hasPublicKey(String nick) {
        return publicKeyStore.containsKey(nick);
    }

    // Pobiera klucz publiczny RSA danego uzytkownika.
    public PublicKey getPublicKey(String nick) {
        return publicKeyStore.get(nick);
    }

    // AES - klucze sesyjne
    // Generuje losowy klucz AES 256-bit, uzywane przy pierwszej wiadomosci prywatnej do danego odbiorcy.
    public SecretKey generateAesKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM);
            keyGenerator.init(AES_KEY_SIZE);
            return keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Fatal: AES algorithm not available", e);
        }
    }

    // Zapisuje klucz sesyjny AES dla konwersacji z danym uzytkownikiem.
    public void storeAesKey(String nick, SecretKey aesKey) {
        aesKeyStore.put(nick, aesKey);
        System.out.println("[CRYPTO] AES session key stored for user: " + nick);
    }


    // Sprawdza czy mamy klucz AES dla konwersacji z danym uzytkownikiem.

    public boolean hasAesKey(String nick) {
        return aesKeyStore.containsKey(nick);
    }

    // Pobiera klucz AES dla konwersacji z danym uzytkownikiem.
    public SecretKey getAesKey(String nick) {
        return aesKeyStore.get(nick);
    }

    // RSA - szyfrowanie/deszyfrowanie klucza AES
    // Szyfruje klucz AES kluczem publicznym RSA odbiorcy.
    public byte[] encryptAesKey(SecretKey aesKey, String recipientNick) {
        try {
            PublicKey recipientPublicKey = publicKeyStore.get(recipientNick);
            if (recipientPublicKey == null) {
                throw new IllegalStateException("No public key found for user: " + recipientNick);
            }
            Cipher cipher = Cipher.getInstance(RSA_CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey);
            return cipher.doFinal(aesKey.getEncoded());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Error encrypting AES key for " + recipientNick + ": " + e.getMessage(), e);
        }
    }

    // Odszyfrowuje otrzymany klucz AES uzywajac wlasnego klucza prywatnego RSA.
    public SecretKey decryptAesKey(byte[] encryptedAesKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedKeyBytes = cipher.doFinal(encryptedAesKey);
            return new SecretKeySpec(decryptedKeyBytes, AES_ALGORITHM);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Error decrypting AES key: " + e.getMessage(), e);
        }
    }

    // AES - szyfrowanie/deszyfrowanie wiadomosci
    // Szyfruje tekst wiadomosci uzywajac klucza AES w trybie GCM.
    public byte[] encryptMessage(String plainText, SecretKey aesKey) {
        try {
            Cipher cipher = Cipher.getInstance(AES_CIPHER_TRANSFORMATION);

            // generujemy losowy IV (12 bajtow)
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(iv);

            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // laczymy IV + ciphertext w jeden byte[]
            byte[] result = new byte[GCM_IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
            System.arraycopy(cipherText, 0, result, GCM_IV_LENGTH, cipherText.length);

            return result;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Error encrypting message: " + e.getMessage(), e);
        }
    }

    // Odszyfrowuje wiadomosc uzywajac klucza AES w trybie GCM.
    public String decryptMessage(byte[] cipherData, SecretKey aesKey) {
        try {
            Cipher cipher = Cipher.getInstance(AES_CIPHER_TRANSFORMATION);

            // wyodrebniamy IV z poczatku danych
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(cipherData, 0, iv, 0, GCM_IV_LENGTH);

            // reszta to zaszyfrowana tresc
            byte[] cipherText = new byte[cipherData.length - GCM_IV_LENGTH];
            System.arraycopy(cipherData, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, gcmSpec);

            byte[] decryptedBytes = cipher.doFinal(cipherText);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Error decrypting message: " + e.getMessage(), e);
        }
    }
}
