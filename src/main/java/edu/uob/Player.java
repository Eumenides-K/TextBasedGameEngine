package edu.uob;

import java.util.HashMap;
import java.util.Map;

public class Player extends Character {
    private int lives=3;
    private Location currentLocation;
    private HashMap<String, Artefact> inventory;

    public Player(String name, String description, Location location) {
        super(name, description);
        currentLocation = location;
        inventory = new HashMap<>();
    }

    public Location getLocation() {
        return currentLocation;
    }

    public void setLocation(Location newLocation){
        currentLocation = newLocation;
    }

    public HashMap<String, Artefact> getInventory(){
        return inventory;
    }

    public void addArtefact(String name, Artefact newArtefact){
        inventory.put(name, newArtefact);
    }

    public void removeArtefact(Artefact artefactToRemove){
        inventory.remove(artefactToRemove.getName());
    }

    public int getLives(){
        return lives;
    }

    public void addLife() {
        if (lives < 3) {
            lives++;
        }
    }
    public void loseLife(){
        if (lives > 1) {
            lives--;
        } else {
            // Reset the lives of dead player and drop all artefacts in current location
            lives = 3;
            for (Map.Entry<String, Artefact> entry : inventory.entrySet()) {
                currentLocation.addArtefact(entry.getValue());
            }
            inventory.clear();
        }
    }
}
