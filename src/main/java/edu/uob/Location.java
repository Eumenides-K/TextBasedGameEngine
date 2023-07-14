package edu.uob;

import java.util.HashMap;

public class Location extends GameEntity {
    private HashMap<String, Artefact> artefacts;
    private HashMap<String, Furniture> furniture;
    private HashMap<String, Character> characters;
    private HashMap<String, Location> paths;
    public Location(String name, String description) {
        super(name, description);
        artefacts = new HashMap<>();
        furniture = new HashMap<>();
        characters = new HashMap<>();
        paths = new HashMap<>();
    }

    public HashMap<String, Artefact> getArtefacts() {
        return artefacts;
    }

    public void addArtefact(Artefact newArtefact){
        artefacts.put(newArtefact.getName(),newArtefact);
    }

    public void removeArtefact(Artefact artefactToRemove){
        artefacts.remove(artefactToRemove.getName());
    }

    public HashMap<String, Furniture> getFurniture(){
        return furniture;
    }

    public void addFurniture(Furniture newFurniture){
        furniture.put(newFurniture.getName(),newFurniture);
    }

    public void removeFurniture(Furniture furnitureToRemove) {
        furniture.remove(furnitureToRemove.getName());
    }

    public HashMap<String, Character> getCharacters(){
        return characters;
    }

    public void addCharacter(Character newCharacter){
        characters.put(newCharacter.getName(), newCharacter);
    }

    public void removeCharacter(Character characterToRemove){
        characters.remove(characterToRemove.getName());
    }

    public HashMap<String, Location> getPaths(){
        return paths;
    }

    public void addPath(Location newPath){
        paths.put(newPath.getName(), newPath);
    }

    public void removePath(Location pathToRemove){
        paths.remove(pathToRemove.getName());
    }
}
