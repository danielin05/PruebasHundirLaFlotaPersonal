package com.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.java_websocket.server.WebSocketServer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONObject;
import org.java_websocket.exceptions.WebsocketNotConnectedException;

public class Main extends WebSocketServer {

    private static final List<String> PLAYER_NAMES = Arrays.asList("A", "B");

    private Map<WebSocket, String> clients;
    private List<String> availableNames;
    private Map<String, JSONObject> clientMousePositions = new HashMap<>();

    private static Map<String, JSONObject> selectableObjects = new HashMap<>();
    private static Map<String, JSONObject> selectableObjects2 = new HashMap<>();

    public Main(InetSocketAddress address) {
        super(address);
        clients = new ConcurrentHashMap<>();
        resetAvailableNames();
    }

    private void resetAvailableNames() {
        availableNames = new ArrayList<>(PLAYER_NAMES);
        Collections.shuffle(availableNames);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String clientName = getNextAvailableName();
        clients.put(conn, clientName);
        System.out.println("WebSocket client connected: " + clientName);
        sendClientsList();
        sendCowntdown();
    }

    private String getNextAvailableName() {
        if (availableNames.isEmpty()) {
            resetAvailableNames();
        }
        return availableNames.remove(0);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String clientName = clients.get(conn);
        clients.remove(conn);
        availableNames.add(clientName);
        System.out.println("WebSocket client disconnected: " + clientName);
        sendClientsList();
    
        // Si no quedan clientes conectados, restablece posiciones de los barcos
        if (clients.isEmpty()) {
            resetShipPositions();
        }
    }
    
    private static final Map<String, JSONObject> initialSelectableObjects = new HashMap<>();
    private static final Map<String, JSONObject> initialSelectableObjects2 = new HashMap<>();

    private void resetShipPositions() {
        // Restablece el estado de `selectableObjects` a su posición inicial
        selectableObjects.clear();
        for (String objectId : initialSelectableObjects.keySet()) {
            selectableObjects.put(objectId, new JSONObject(initialSelectableObjects.get(objectId).toString()));
        }
        // Restablece el estado de `selectableObjects2` a su posición inicial
        selectableObjects2.clear();
        for (String objectId : initialSelectableObjects2.keySet()) {
            selectableObjects2.put(objectId, new JSONObject(initialSelectableObjects2.get(objectId).toString()));
        }
    
        // Enviar actualización a los clientes
        sendServerSelectableObjects();
    }
    

    @Override
    public void onMessage(WebSocket conn, String message) {
        JSONObject obj = new JSONObject(message);
    
        
        if (obj.has("type")) {
            String type = obj.getString("type");
    
            switch (type) {
                case "clientMouseMoving":
                    // Obtenim el clientId del missatge
                    String clientId = obj.getString("clientId");   
                    clientMousePositions.put(clientId, obj);
        
                    // Prepara el missatge de tipus 'serverMouseMoving' amb les posicions de tots els clients
                    JSONObject rst0 = new JSONObject();
                    rst0.put("type", "serverMouseMoving");
                    rst0.put("positions", clientMousePositions);
        
                    // Envia el missatge a tots els clients connectats
                    broadcastMessage(rst0.toString(), null);
                    break;
                case "clientSelectableObjectMoving":
                    String objectId = obj.getString("objectId");
                    selectableObjects.put(objectId, obj);

                    sendServerSelectableObjects();
                    break;
            }
        }
    }
   
    private void broadcastMessage(String message, WebSocket sender) {
        for (Map.Entry<WebSocket, String> entry : clients.entrySet()) {
            WebSocket conn = entry.getKey();
            if (conn != sender) {
                try {
                    conn.send(message);
                } catch (WebsocketNotConnectedException e) {
                    System.out.println("Client " + entry.getValue() + " not connected.");
                    clients.remove(conn);
                    availableNames.add(entry.getValue());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendPrivateMessage(String destination, String message, WebSocket senderConn) {
        boolean found = false;

        for (Map.Entry<WebSocket, String> entry : clients.entrySet()) {
            if (entry.getValue().equals(destination)) {
                found = true;
                try {
                    entry.getKey().send(message);
                    JSONObject confirmation = new JSONObject();
                    confirmation.put("type", "confirmation");
                    confirmation.put("message", "Message sent to " + destination);
                    senderConn.send(confirmation.toString());
                } catch (WebsocketNotConnectedException e) {
                    System.out.println("Client " + destination + " not connected.");
                    clients.remove(entry.getKey());
                    availableNames.add(destination);
                    notifySenderClientUnavailable(senderConn, destination);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            }
        }

        if (!found) {
            System.out.println("Client " + destination + " not found.");
            notifySenderClientUnavailable(senderConn, destination);
        }
    }

    private void notifySenderClientUnavailable(WebSocket sender, String destination) {
        JSONObject rst = new JSONObject();
        rst.put("type", "error");
        rst.put("message", "Client " + destination + " not available.");

        try {
            sender.send(rst.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendClientsList() {
        JSONArray clientList = new JSONArray();
        for (String clientName : clients.values()) {
            clientList.put(clientName);
        }

        Iterator<Map.Entry<WebSocket, String>> iterator = clients.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<WebSocket, String> entry = iterator.next();
            WebSocket conn = entry.getKey();
            String clientName = entry.getValue();

            JSONObject rst = new JSONObject();
            rst.put("type", "clients");
            rst.put("id", clientName);
            rst.put("list", clientList);

            try {
                conn.send(rst.toString());
            } catch (WebsocketNotConnectedException e) {
                System.out.println("Client " + clientName + " not connected.");
                iterator.remove();
                availableNames.add(clientName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void sendCowntdown() {
        int requiredNumberOfClients = 2;
        if (clients.size() == requiredNumberOfClients) {
            for (int i = 5; i >= 0; i--) {
                JSONObject msg = new JSONObject();
                msg.put("type", "countdown");
                msg.put("value", i);
                broadcastMessage(msg.toString(), null);
                if (i == 0) {
                    sendServerSelectableObjects();
                } else {
                    try {
                        Thread.sleep(750);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void sendServerSelectableObjects() {
        for (Map.Entry<WebSocket, String> entry : clients.entrySet()) {
            WebSocket conn = entry.getKey();
            String clientId = entry.getValue();
    
            // Crear el mensaje para enviar solo los objetos del cliente correspondiente
            JSONObject rst = new JSONObject();
            rst.put("type", "serverSelectableObjects");
    
            if (clientId.equals("A")) {
                rst.put("selectableObjects", selectableObjects);  // Barcos del jugador A
            } else if (clientId.equals("B")) {
                rst.put("selectableObjects", selectableObjects2); // Barcos del jugador B
            }
    
            try {
                conn.send(rst.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
   
    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket server started on port: " + getPort());
        setConnectionLostTimeout(0);
        setConnectionLostTimeout(100);
    }

    public static String askSystemName() {
        StringBuilder resultat = new StringBuilder();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("uname", "-r");
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                resultat.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return "Error: El procés ha finalitzat amb codi " + exitCode;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
        return resultat.toString().trim();
    }

    public static void main(String[] args) {

        String systemName = askSystemName();

        // WebSockets server
        Main server = new Main(new InetSocketAddress(3000));
        server.start();
        
        LineReader reader = LineReaderBuilder.builder().build();
        System.out.println("Server running. Type 'exit' to gracefully stop it.");

        // Add objects Player1
        String name0 = "O0";
        JSONObject obj0 = new JSONObject();
        obj0.put("objectId", name0);
        obj0.put("x", 300);
        obj0.put("y", 50);
        obj0.put("initialX", 300);
        obj0.put("initialY", 50);
        obj0.put("cols", 4);
        obj0.put("rows", 1);
        selectableObjects.put(name0, obj0);
        initialSelectableObjects.put("O0", obj0);

        String name1 = "O1";
        JSONObject obj1 = new JSONObject();
        obj1.put("objectId", name1);
        obj1.put("x", 300);
        obj1.put("y", 100);
        obj1.put("initialX", 300);
        obj1.put("initialY", 100);
        obj1.put("cols", 3);
        obj1.put("rows", 1);
        selectableObjects.put(name1, obj1);
        initialSelectableObjects.put("O1", obj1);

        String name2 = "O2";
        JSONObject obj2 = new JSONObject();
        obj2.put("objectId", name2);
        obj2.put("x", 300);
        obj2.put("y", 150);
        obj2.put("initialX", 300);
        obj2.put("initialY", 150);
        obj2.put("cols", 1);
        obj2.put("rows", 2);
        selectableObjects.put(name2, obj2);
        initialSelectableObjects.put("O2", obj2);

        String name3 = "O3";
        JSONObject obj3 = new JSONObject();
        obj3.put("objectId", name3);
        obj3.put("x", 350);
        obj3.put("y", 150);
        obj3.put("initialX", 350);
        obj3.put("initialY", 150);
        obj3.put("cols", 1);
        obj3.put("rows", 3);
        selectableObjects.put(name3, obj3);
        initialSelectableObjects.put("O3", obj3);

        String name4 = "O4";
        JSONObject obj4 = new JSONObject();
        obj4.put("objectId", name4);
        obj4.put("x", 400);
        obj4.put("y", 150);
        obj4.put("initialX", 400);
        obj4.put("initialY", 150);
        obj4.put("cols", 1);
        obj4.put("rows", 5);
        selectableObjects.put(name4, obj4);
        initialSelectableObjects.put("O4", obj4);

        // Add objects Player2
        String name5 = "O5";
        JSONObject obj5 = new JSONObject();
        obj5.put("objectId", name5);
        obj5.put("x", 300);
        obj5.put("y", 50);
        obj5.put("initialX", 300);
        obj5.put("initialY", 50);
        obj5.put("cols", 4);
        obj5.put("rows", 1);
        selectableObjects2.put(name5, obj5);
        initialSelectableObjects2.put("O5", obj5);

        String name6 = "O6";
        JSONObject obj6 = new JSONObject();
        obj6.put("objectId", name6);
        obj6.put("x", 300);
        obj6.put("y", 100);
        obj6.put("initialX", 300);
        obj6.put("initialY", 100);
        obj6.put("cols", 3);
        obj6.put("rows", 1);
        selectableObjects2.put(name6, obj6);
        initialSelectableObjects2.put("O6", obj6);

        String name7 = "O7";
        JSONObject obj7 = new JSONObject();
        obj7.put("objectId", name7);
        obj7.put("x", 300);
        obj7.put("y", 150);
        obj7.put("initialX", 300);
        obj7.put("initialY", 150);
        obj7.put("cols", 1);
        obj7.put("rows", 2);
        selectableObjects2.put(name7, obj7);
        initialSelectableObjects2.put("O7", obj7);

        String name8 = "O8";
        JSONObject obj8 = new JSONObject();
        obj8.put("objectId", name8);
        obj8.put("x", 350);
        obj8.put("y", 150);
        obj8.put("initialX", 350);
        obj8.put("initialY", 150);
        obj8.put("cols", 1);
        obj8.put("rows", 3);
        selectableObjects2.put(name8, obj8);
        initialSelectableObjects2.put("O8", obj8);

        String name9 = "O9";
        JSONObject obj9 = new JSONObject();
        obj9.put("objectId", name9);
        obj9.put("x", 400);
        obj9.put("y", 150);
        obj9.put("initialX", 400);
        obj9.put("initialY", 150);
        obj9.put("cols", 1);
        obj9.put("rows", 5);
        selectableObjects2.put(name9, obj9);
        initialSelectableObjects2.put("O9", obj9);

        try {
            while (true) {
                String line = null;
                try {
                    line = reader.readLine("> ");
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }

                line = line.trim();

                if (line.equalsIgnoreCase("exit")) {
                    System.out.println("Stopping server...");
                    try {
                        server.stop(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;
                } else {
                    System.out.println("Unknown command. Type 'exit' to stop server gracefully.");
                }
            }
        } finally {
            System.out.println("Server stopped.");
        }
    }
}