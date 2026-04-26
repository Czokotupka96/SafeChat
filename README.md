Serwer:
mvn clean compile; mvn exec:java "-Dexec.mainClass=com.safechat.server.ServerMain"

Klient:
mvn exec:java "-Dexec.mainClass=com.safechat.client.ClientMain"