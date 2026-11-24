package utb.fai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class ImServerGuiApplicationTests {

	@Autowired
	private ActiveHandlers activeHandlers;

	@Test
	void contextLoads() {
		assertNotNull(activeHandlers);
	}

	@Test
	void testTcpTelnetChatProtocol() throws Exception {
		Thread.sleep(500);

		// Connect client 1: Alice
		try (Socket aliceSocket = new Socket("localhost", 33000);
			 BufferedReader aliceIn = new BufferedReader(new InputStreamReader(aliceSocket.getInputStream(), "UTF-8"));
			 OutputStreamWriter aliceOut = new OutputStreamWriter(aliceSocket.getOutputStream(), "UTF-8")) {

			String prompt = aliceIn.readLine();
			assertEquals("Enter your name:", prompt);

			// Register name Alice
			aliceOut.write("Alice\r\n");
			aliceOut.flush();
			Thread.sleep(200);

			// Check groups
			aliceOut.write("#groups\r\n");
			aliceOut.flush();
			String groups = aliceIn.readLine();
			assertTrue(groups.contains("public"));

			// Connect client 2: Bob
			try (Socket bobSocket = new Socket("localhost", 33000);
				 BufferedReader bobIn = new BufferedReader(new InputStreamReader(bobSocket.getInputStream(), "UTF-8"));
				 OutputStreamWriter bobOut = new OutputStreamWriter(bobSocket.getOutputStream(), "UTF-8")) {

				assertEquals("Enter your name:", bobIn.readLine());
				bobOut.write("Bob\r\n");
				bobOut.flush();
				Thread.sleep(200);

				// Alice sends broadcast message
				aliceOut.write("Ahoj vsetci\r\n");
				aliceOut.flush();

				String bobReceived = bobIn.readLine();
				assertEquals("[Alice] >> Ahoj vsetci", bobReceived);

				// Bob sends private message to Alice
				bobOut.write("#sendPrivate Alice TajnaSprava\r\n");
				bobOut.flush();

				String aliceReceivedPrivate = aliceIn.readLine();
				assertEquals("[Bob] >> TajnaSprava", aliceReceivedPrivate);
			}
		}
	}

	@Test
	void testWebSocketAndCrossPlatformChat() throws Exception {
		Thread.sleep(500);

		StandardWebSocketClient wsClient = new StandardWebSocketClient();
		ArrayBlockingQueue<String> wsMessages = new ArrayBlockingQueue<>(10);

		WebSocketSession wsSession = wsClient.execute(new TextWebSocketHandler() {
			@Override
			protected void handleTextMessage(WebSocketSession session, TextMessage message) {
				wsMessages.offer(message.getPayload());
			}
		}, "ws://localhost:8080/ws").get(5, TimeUnit.SECONDS);

		assertNotNull(wsSession);
		assertTrue(wsSession.isOpen());

		// 1. Should receive CONNECTED event
		String connectedMsg = wsMessages.poll(3, TimeUnit.SECONDS);
		assertNotNull(connectedMsg);
		assertTrue(connectedMsg.contains("CONNECTED"));

		// 2. Set username via WebSocket
		wsSession.sendMessage(new TextMessage("{\"action\":\"SET_NAME\",\"name\":\"WebTester\"}"));
		String nameSetMsg = wsMessages.poll(3, TimeUnit.SECONDS);
		assertNotNull(nameSetMsg);
		assertTrue(nameSetMsg.contains("NAME_SET") || nameSetMsg.contains("WebTester"));

		// 3. Connect a Telnet client and send message to WebTester
		try (Socket telnetSocket = new Socket("localhost", 33000);
			 BufferedReader telnetIn = new BufferedReader(new InputStreamReader(telnetSocket.getInputStream(), "UTF-8"));
			 OutputStreamWriter telnetOut = new OutputStreamWriter(telnetSocket.getOutputStream(), "UTF-8")) {

			assertEquals("Enter your name:", telnetIn.readLine());
			telnetOut.write("TelnetUser\r\n");
			telnetOut.flush();
			Thread.sleep(200);

			// Telnet sends private message to WebTester
			telnetOut.write("#sendPrivate WebTester HelloFromTerminal\r\n");
			telnetOut.flush();

			// WebSocket should receive PRIVATE event
			String privateReceivedOnWs = null;
			for (int i = 0; i < 5; i++) {
				String m = wsMessages.poll(2, TimeUnit.SECONDS);
				if (m != null && m.contains("HelloFromTerminal")) {
					privateReceivedOnWs = m;
					break;
				}
			}
			assertNotNull(privateReceivedOnWs);
			assertTrue(privateReceivedOnWs.contains("HelloFromTerminal"));
			assertTrue(privateReceivedOnWs.contains("TelnetUser"));
		}

		wsSession.close();
	}
}


