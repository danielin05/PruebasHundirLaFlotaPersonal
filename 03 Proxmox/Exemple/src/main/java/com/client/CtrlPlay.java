package com.client;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.json.JSONObject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

public class CtrlPlay implements Initializable {

    @FXML
    private Canvas canvas;
    private GraphicsContext gc;
    private Boolean showFPS = false;

    private PlayTimer animationTimer;
    private PlayGrid grid;

    public Map<String, JSONObject> clientMousePositions = new HashMap<>();
    private Boolean mouseDragging = false;
    private double mouseOffsetX, mouseOffsetY;

    public static Map<String, JSONObject> selectableObjects = new HashMap<>();
    private String selectedObject = "";
    public static Map<String, JSONObject> selectableObjects2 = new HashMap<>();
    private String selectedObject2 = "";

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // Get drawing context
        this.gc = canvas.getGraphicsContext2D();

        // Set listeners
        UtilsViews.parentContainer.heightProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        UtilsViews.parentContainer.widthProperty().addListener((observable, oldValue, newvalue) -> { onSizeChanged(); });
        
        canvas.setOnMouseMoved(this::setOnMouseMoved);
        canvas.setOnMousePressed(this::onMousePressed);
        canvas.setOnMouseDragged(this::onMouseDragged);
        canvas.setOnMouseReleased(this::onMouseReleased);

        // Define grid
        grid = new PlayGrid(25, 25, 25, 10, 10);

        // Start run/draw timer bucle
        animationTimer = new PlayTimer(this::run, this::draw, 0);
        start();
    }

    // When window changes its size
    public void onSizeChanged() {

        double width = UtilsViews.parentContainer.getWidth();
        double height = UtilsViews.parentContainer.getHeight();
        canvas.setWidth(width);
        canvas.setHeight(height);
    }

    // Start animation timer
    public void start() {
        animationTimer.start();
    }

    // Stop animation timer
    public void stop() {
        animationTimer.stop();
    }

    private void setOnMouseMoved(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();

        JSONObject newPosition = new JSONObject();
        newPosition.put("x", mouseX);
        newPosition.put("y", mouseY);
        if (grid.isPositionInsideGrid(mouseX, mouseY)) {                
            newPosition.put("col", grid.getCol(mouseX));
            newPosition.put("row", grid.getRow(mouseY));
        } else {
            newPosition.put("col", -1);
            newPosition.put("row", -1);
        }
        clientMousePositions.put(Main.clientId, newPosition);

        JSONObject msgObj = clientMousePositions.get(Main.clientId);
        msgObj.put("type", "clientMouseMoving");
        msgObj.put("clientId", Main.clientId);
    
        if (Main.wsClient != null) {
            Main.wsClient.safeSend(msgObj.toString());
        }
    }

    private int initialX, initialY;

    private void onMousePressed(MouseEvent event) {
        double mouseX = event.getX();
        double mouseY = event.getY();
    
        selectedObject = "";
        selectedObject2 = "";
        mouseDragging = false;
    
        // Iterar sobre selectableObjects
        for (String objectId : selectableObjects.keySet()) {
            JSONObject obj = selectableObjects.get(objectId);
            int objX = obj.getInt("x");
            int objY = obj.getInt("y");
            int cols = obj.getInt("cols");
            int rows = obj.getInt("rows");
            initialX = obj.getInt("initialX");
            initialY = obj.getInt("initialY");
    
            if (isPositionInsideObject(mouseX, mouseY, objX, objY, cols, rows)) {
                selectedObject = objectId;
                mouseDragging = true;
                mouseOffsetX = event.getX() - objX;
                mouseOffsetY = event.getY() - objY;
                break;
            }
        }
    
        // Iterar sobre selectableObjects2
        for (String objectId : selectableObjects2.keySet()) {
            JSONObject obj = selectableObjects2.get(objectId);
            int objX = obj.getInt("x");
            int objY = obj.getInt("y");
            int cols = obj.getInt("cols");
            int rows = obj.getInt("rows");
            initialX = obj.getInt("initialX");
            initialY = obj.getInt("initialY");
    
            if (isPositionInsideObject(mouseX, mouseY, objX, objY, cols, rows)) {
                selectedObject2 = objectId;
                mouseDragging = true;
                mouseOffsetX = event.getX() - objX;
                mouseOffsetY = event.getY() - objY;
                break;
            }
        }
    }

    private void onMouseDragged(MouseEvent event) {
        if (mouseDragging) {
            if (!selectedObject.isEmpty()) {
                JSONObject obj = selectableObjects.get(selectedObject);
                double objX = event.getX() - mouseOffsetX;
                double objY = event.getY() - mouseOffsetY;
    
                obj.put("x", objX);
                obj.put("y", objY);
                obj.put("col", grid.getCol(objX));
                obj.put("row", grid.getRow(objY));
    
                JSONObject msgObj = selectableObjects.get(selectedObject);
                msgObj.put("type", "clientSelectableObjectMoving");
                msgObj.put("objectId", obj.getString("objectId"));
    
                if (Main.wsClient != null) {
                    Main.wsClient.safeSend(msgObj.toString());
                }
            } else if (!selectedObject2.isEmpty()) {
                JSONObject obj = selectableObjects2.get(selectedObject2);
                double objX = event.getX() - mouseOffsetX;
                double objY = event.getY() - mouseOffsetY;
    
                obj.put("x", objX);
                obj.put("y", objY);
                obj.put("col", grid.getCol(objX));
                obj.put("row", grid.getRow(objY));
    
                JSONObject msgObj = selectableObjects2.get(selectedObject2);
                msgObj.put("type", "clientSelectableObjectMoving");
                msgObj.put("objectId", obj.getString("objectId"));
    
                if (Main.wsClient != null) {
                    Main.wsClient.safeSend(msgObj.toString());
                }
            }
        }
        setOnMouseMoved(event);
    }

    private void onMouseReleased(MouseEvent event) {
        if (!selectedObject.isEmpty()) {
            JSONObject obj = selectableObjects.get(selectedObject);
            int objCol = obj.getInt("col");
            int objRow = obj.getInt("row");
            int cols = obj.getInt("cols");
            int rows = obj.getInt("rows");
    
            if (isCompletelyInsideGrid(objCol, objRow, cols, rows) &&
                !isOverlapping(objCol, objRow, cols, rows, selectedObject)) {
                obj.put("x", grid.getCellX(objCol));
                obj.put("y", grid.getCellY(objRow));
            } else {
                obj.put("x", initialX);
                obj.put("y", initialY);
            }
    
            JSONObject msgObj = selectableObjects.get(selectedObject);
            msgObj.put("type", "clientSelectableObjectMoving");
            msgObj.put("objectId", obj.getString("objectId"));
    
            if (Main.wsClient != null) {
                Main.wsClient.safeSend(msgObj.toString());
            }
    
            mouseDragging = false;
            selectedObject = "";
        } else if (!selectedObject2.isEmpty()) {
            JSONObject obj = selectableObjects2.get(selectedObject2);
            int objCol = obj.getInt("col");
            int objRow = obj.getInt("row");
            int cols = obj.getInt("cols");
            int rows = obj.getInt("rows");
    
            if (isCompletelyInsideGrid(objCol, objRow, cols, rows) &&
                !isOverlapping(objCol, objRow, cols, rows, selectedObject2)) {
                obj.put("x", grid.getCellX(objCol));
                obj.put("y", grid.getCellY(objRow));
            } else {
                obj.put("x", initialX);
                obj.put("y", initialY);
            }
    
            JSONObject msgObj = selectableObjects2.get(selectedObject2);
            msgObj.put("type", "clientSelectableObjectMoving");
            msgObj.put("objectId", obj.getString("objectId"));
    
            if (Main.wsClient != null) {
                Main.wsClient.safeSend(msgObj.toString());
            }
    
            mouseDragging = false;
            selectedObject2 = "";
        }
    }
    
    private boolean isCompletelyInsideGrid(int startCol, int startRow, int cols, int rows) {
        // Verifica si el barco está completamente dentro de los límites del grid
        return startCol >= 0 && startRow >= 0 &&
               (startCol + cols) <= grid.getCols() &&
               (startRow + rows) <= grid.getRows();
    }
    
    
    // Método para verificar la superposición
    private boolean isOverlapping(int startCol, int startRow, int cols, int rows, String currentObjectId) {
        for (String objectId : selectableObjects.keySet()) {
            if (!objectId.equals(currentObjectId)) {
                JSONObject otherObj = selectableObjects.get(objectId);
                
                // Verificar que 'col' y 'row' existan en el objeto
                if (!otherObj.has("col") || !otherObj.has("row")) {
                    continue; // Saltar este objeto si no tiene coordenadas válidas
                }
    
                int otherCol = otherObj.getInt("col");
                int otherRow = otherObj.getInt("row");
                int otherCols = otherObj.getInt("cols");
                int otherRows = otherObj.getInt("rows");
    
                // Verificar si alguna de las celdas del barco actual se solapa con las del otro barco
                for (int col = startCol; col < startCol + cols; col++) {
                    for (int row = startRow; row < startRow + rows; row++) {
                        if (col >= otherCol && col < otherCol + otherCols &&
                            row >= otherRow && row < otherRow + otherRows) {
                            return true; // Hay superposición
                        }
                    }
                }
            }
        }
        return false;
    }
    
    
    
        

    public void setPlayersMousePositions(JSONObject positions) {
        clientMousePositions.clear();
        for (String clientId : positions.keySet()) {
            JSONObject positionObject = positions.getJSONObject(clientId);
            clientMousePositions.put(clientId, positionObject);
        }
    }

    public void setSelectableObjects(JSONObject objects) {
        selectableObjects.clear();
        for (String objectId : objects.keySet()) {
            JSONObject positionObject = objects.getJSONObject(objectId);
            selectableObjects.put(objectId, positionObject);
        }
    }

    public Boolean isPositionInsideObject(double positionX, double positionY, int objX, int objY, int cols, int rows) {
        double cellSize = grid.getCellSize();
        double objectWidth = cols * cellSize;
        double objectHeight = rows * cellSize;

        double objectLeftX = objX;
        double objectRightX = objX + objectWidth;
        double objectTopY = objY;
        double objectBottomY = objY + objectHeight;

        return positionX >= objectLeftX && positionX < objectRightX &&
               positionY >= objectTopY && positionY < objectBottomY;
    }

    // Run game (and animations)
    private void run(double fps) {

        if (animationTimer.fps < 1) { return; }

        // Update objects and animations here
    }

    // Draw game to canvas
    public void draw() {

        // Clean drawing area
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Draw colored 'over' cells

        for (String clientId : clientMousePositions.keySet()) {
            JSONObject position = clientMousePositions.get(clientId);

            int col = position.getInt("col");
            int row = position.getInt("row");

            // Comprovar si està dins dels límits de la graella
            if (row >= 0 && col >= 0) {
                if ("A".equals(clientId)) {
                    gc.setFill(Color.LIGHTBLUE); 
                } else {
                    gc.setFill(Color.LIGHTGREEN); 
                }
                // Emplenar la casella amb el color clar
                gc.fillRect(grid.getCellX(col), grid.getCellY(row), grid.getCellSize(), grid.getCellSize());
            }
        }

        // Draw grid
        drawGrid();

        for (String clientId : clientMousePositions.keySet()) {
        JSONObject position = clientMousePositions.get(clientId);
        if ("A".equals(clientId)) {
            // Draw selectable objects
            for (String objectId : selectableObjects.keySet()) {
                JSONObject selectableObject = selectableObjects.get(objectId);
                drawSelectableObject(objectId, selectableObject);
            }
        } else {
            // Draw selectable objects
            for (String objectId : selectableObjects2.keySet()) {
                JSONObject selectableObject = selectableObjects2.get(objectId);
                drawSelectableObject(objectId, selectableObject);
            }
        }
    }



        // Draw mouse circles
        for (String clientId : clientMousePositions.keySet()) {
            JSONObject position = clientMousePositions.get(clientId);
            if ("A".equals(clientId)) {
                gc.setFill(Color.BLUE);
            } else {
                gc.setFill(Color.GREEN); 
            }
            gc.fillOval(position.getInt("x") - 5, position.getInt("y") - 5, 10, 10);
        }

        // Draw FPS if needed
        if (showFPS) { animationTimer.drawFPS(gc); }   
    }

    public void drawGrid() {
        gc.setStroke(Color.BLACK);

        for (int row = 0; row < grid.getRows(); row++) {
            for (int col = 0; col < grid.getCols(); col++) {
                double cellSize = grid.getCellSize();
                double x = grid.getStartX() + col * cellSize;
                double y = grid.getStartY() + row * cellSize;
                gc.strokeRect(x, y, cellSize, cellSize);
            }
        }
    }

    public void drawSelectableObject(String objectId, JSONObject obj) {
        double cellSize = grid.getCellSize();

        int x = obj.getInt("x");
        int y = obj.getInt("y");
        double width = obj.getInt("cols") * cellSize;
        double height = obj.getInt("rows") * cellSize;

        // Seleccionar un color basat en l'objectId
        Color color;
        switch (objectId.toLowerCase()) {
            case "red":
                color = Color.RED;
                break;
            case "blue":
                color = Color.BLUE;
                break;
            case "green":
                color = Color.GREEN;
                break;
            case "yellow":
                color = Color.YELLOW;
                break;
            default:
                color = Color.GRAY;
                break;
        }

        // Dibuixar el rectangle
        gc.setFill(color);
        gc.fillRect(x, y, width, height);

        // Dibuixar el contorn
        gc.setStroke(Color.BLACK);
        gc.strokeRect(x, y, width, height);

        // Opcionalment, afegir text (per exemple, l'objectId)
        gc.setFill(Color.BLACK);
        gc.fillText(objectId, x + 5, y + 15);
    }
}
