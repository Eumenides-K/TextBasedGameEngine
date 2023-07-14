package edu.uob;

import com.alexmerz.graphviz.ParseException;
import com.alexmerz.graphviz.Parser;
import com.alexmerz.graphviz.objects.Edge;
import com.alexmerz.graphviz.objects.Graph;
import com.alexmerz.graphviz.objects.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

public final class GameServer {

    private static final char END_OF_TRANSMISSION = 4;
    private final HashMap<String, Location> locations;
    private final HashMap<String, Artefact> artefacts;
    private final HashMap<String, Furniture> furniture;
    private final HashMap<String, Character> characters;
    private final HashMap<String, Player> players;
    private final HashMap<String, HashSet<GameAction>> actions;
    private final HashSet<String> builtInCommands = new HashSet<>(Arrays.asList("inventory", "inv", "get", "drop", "goto", "look", "health"));
    private Location startLocation;
    private Player currentPlayer;
    private int numberOfPlayers = 0;

    public static void main(String[] args) throws IOException {
        File entitiesFile = Paths.get("config" + File.separator + "basic-entities.dot").toAbsolutePath().toFile();
        File actionsFile = Paths.get("config" + File.separator + "basic-actions.xml").toAbsolutePath().toFile();
        GameServer server = new GameServer(entitiesFile, actionsFile);
        server.blockingListenOn(8888);
    }

    public GameServer(File entitiesFile, File actionsFile) {
        locations = new HashMap<>();
        artefacts = new HashMap<>();
        furniture = new HashMap<>();
        characters = new HashMap<>();
        players = new HashMap<>();
        actions = new HashMap<>();

        loadEntities(entitiesFile);
        loadActions(actionsFile);
    }

    private void loadEntities(File entitiesFile) {
        try {
            Parser parser = new Parser();
            FileReader reader = new FileReader(entitiesFile);
            parser.parse(reader);
            Graph wholeDocument = parser.getGraphs().get(0);
            ArrayList<Graph> sections = wholeDocument.getSubgraphs();

            // Read locations
            ArrayList<Graph> locations = sections.get(0).getSubgraphs();
            for (int i = 0; i < locations.size(); i++){
                Graph location = locations.get(i);
                String name = location.getNodes(false).get(0).getId().getId().toLowerCase();
                String description = location.getNodes(false).get(0).getAttribute("description");
                Location newLocation = new Location(name,description);
                for (Graph subgraph : location.getSubgraphs()){
                    for (Node node : subgraph.getNodes(false)){
                        String entityName = node.getId().getId().toLowerCase();
                        String entityDescription = node.getAttribute("description");
                        String entityType = subgraph.getId().getId();
                        switch (entityType){
                            case "artefacts":
                                Artefact artefact = new Artefact(entityName,entityDescription);
                                newLocation.addArtefact(artefact);
                                artefacts.put(entityName, artefact);
                                break;
                            case "furniture":
                                Furniture furniture = new Furniture(entityName,entityDescription);
                                newLocation.addFurniture(furniture);
                                this.furniture.put(entityName, furniture);
                                break;
                            case "characters":
                                Character character = new Character(entityName,entityDescription);
                                newLocation.addCharacter(character);
                                characters.put(entityName, character);
                                break;
                            default:
                                break;
                        }
                    }
                }
                if (i == 0){
                    startLocation = newLocation;
                }
                this.locations.put(name, newLocation);
            }

            // Read paths
            ArrayList<Edge> paths = sections.get(1).getEdges();
            for (Edge path : paths){
                String fromId = path.getSource().getNode().getId().getId();
                String toId = path.getTarget().getNode().getId().getId();
                Location fromLocation = this.locations.get(fromId);
                Location toLocation = this.locations.get(toId);
                fromLocation.addPath(toLocation);
            }

            // Check if there is a storeroom
            if (!this.locations.containsKey("storeroom")) {
                Location storeRoom = new Location("storeroom", "Storage for any entities not placed in the game");
                this.locations.put(storeRoom.getName(),storeRoom);
            }
        } catch (FileNotFoundException fileNotFoundException) {
            System.out.println("FileNotFoundException was thrown when attempting to read the entities file");
        } catch (ParseException pe) {
            System.out.println("ParseException was thrown when attempting to read the entities file");
        }
    }

    private void loadActions(File actionsFile) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document document = builder.parse(actionsFile);
            Element root = document.getDocumentElement();
            NodeList actions = root.getChildNodes();
            for (int i = 1; i < actions.getLength(); i=i+2) {
                GameAction gameAction= new GameAction();
                Element action = (Element)actions.item(i);

                // Load trigger key phrases
                Element triggerNodes = (Element)action.getElementsByTagName("triggers").item(0);
                HashSet<String> triggers = new HashSet<>();
                for (int j = 0; j < triggerNodes.getElementsByTagName("keyphrase").getLength(); j++) {
                    String triggerPhrase = triggerNodes.getElementsByTagName("keyphrase").item(j).getTextContent().toLowerCase();
                    triggers.add(triggerPhrase);
                }
                gameAction.setTriggers(triggers);

                // Load subjects
                Element subjectNodes = (Element)action.getElementsByTagName("subjects").item(0);
                HashSet<String> subjects = new HashSet<>();
                for (int j = 0; j < subjectNodes.getElementsByTagName("entity").getLength(); j++) {
                    String subjectPhrase = subjectNodes.getElementsByTagName("entity").item(j).getTextContent().toLowerCase();
                    subjects.add(subjectPhrase);
                }
                gameAction.setSubjects(subjects);

                // Load consumed
                Element consumedNodes = (Element)action.getElementsByTagName("consumed").item(0);
                HashSet<String> consumed = new HashSet<>();
                for (int j = 0; j < consumedNodes.getElementsByTagName("entity").getLength(); j++) {
                    String consumedPhrase = consumedNodes.getElementsByTagName("entity").item(j).getTextContent().toLowerCase();
                    consumed.add(consumedPhrase);
                }
                gameAction.setConsumed(consumed);

                // Load produced
                Element producedNodes = (Element)action.getElementsByTagName("produced").item(0);
                HashSet<String> produced = new HashSet<>();
                for (int j = 0; j < producedNodes.getElementsByTagName("entity").getLength(); j++) {
                    String producedPhrase = producedNodes.getElementsByTagName("entity").item(j).getTextContent().toLowerCase();
                    produced.add(producedPhrase);
                }
                gameAction.setProduced(produced);

                // Load the narration
                gameAction.setNarration(action.getElementsByTagName("narration").item(0).getTextContent());

                // Load to the server
                for (String trigger : triggers) {
                    if (this.actions.containsKey(trigger)) {
                        this.actions.get(trigger).add(gameAction);
                    }
                    else {
                        HashSet<GameAction> newActions = new HashSet<>();
                        newActions.add(gameAction);
                        this.actions.put(trigger, newActions);
                    }
                }
            }
        } catch(ParserConfigurationException pce) {
            System.out.println("ParserConfigurationException was thrown when attempting to read the actions file");
        } catch(SAXException saxe) {
            System.out.println("SAXException was thrown when attempting to read the actions file");
        } catch(IOException ioe) {
            System.out.println("IOException was thrown when attempting to read basic actions file");
        }
    }

    public String handleCommand(String command) {
        // Set current player
        int colonPosition = command.indexOf(":");
        if (colonPosition != -1) {
            String username = command.substring(0, colonPosition).trim().toLowerCase();
            if (players.containsKey(username)) {
                currentPlayer = players.get(username);
            } else {
                if(!isValidPlayerName(username) || username.length() > 10) {
                    return "Invalid username.";
                }
                numberOfPlayers++;
                String playerDescription = "Player " + numberOfPlayers;
                Player newPlayer = new Player(username, playerDescription, startLocation);
                startLocation.addCharacter(newPlayer);
                players.put(username, newPlayer);
                currentPlayer = newPlayer;
            }
        } else {
            return "Invalid command. No username found.";
        }

        // Format the command
        String restOfCommand = command.substring(colonPosition + 1);
        String formatCommand = restOfCommand.toLowerCase().trim().replaceAll("[^a-zA-Z\\s]", "");

        // Check for built-in commands keywords
        String[] words = formatCommand.split("\\s+");
        List<String> wordList = Arrays.asList(words);
        String mainCommand = null;
        for (String word : wordList) {
            if (builtInCommands.contains(word)) {
                if (mainCommand == null) {
                    mainCommand = word;
                } else {
                    return "Invalid command. Please provide a single action at a time.";
                }
            }
        }

        // Check for action trigger key phrases
        ArrayList<String> keyphraseFound = new ArrayList<>();
        String adjustedCommand = " " + formatCommand + " ";
        for (String keyphrase : actions.keySet()) {
            String adjustedKeyphrase = " " + keyphrase + " ";
            if (adjustedCommand.contains(adjustedKeyphrase)) {
                if (mainCommand == null) {
                    keyphraseFound.add(keyphrase);
                } else {
                    return "Invalid command. Please provide a single action at a time.";
                }
            }
        }

        // Check if there is a built-in command keyphrase or an action keywords
        if (mainCommand == null && keyphraseFound.size() == 0) {
            return "Invalid command. Please specify an action.";
        } else if (keyphraseFound.size() != 0) {
            mainCommand = "actions";
        }

        // Handle different commands
        switch (mainCommand) {
            case "inventory":
            case "inv":
                return handleInv(adjustedCommand);
            case "get":
                return handleGet(adjustedCommand);
            case "drop":
                return handleDrop(adjustedCommand);
            case "goto":
                return handleGoto(adjustedCommand);
            case "look":
                return handleLook(adjustedCommand);
            case "health":
                return handleHealth(adjustedCommand);
            case "actions":
                return handleAction(keyphraseFound, adjustedCommand);
            default:
                return "Unknown error. Unknown command type.";
        }
    }

    public static boolean isValidPlayerName(String playerName) {
        String regex = "^[A-Za-z\\s'-]+$";
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(playerName).matches();
    }

    private String handleInv(String adjustedCommand) {
        // Check extraneous entities
        if (!checkExtraneousArtefactEntities(adjustedCommand) ||
            !checkExtraneousFurnitureEntities(adjustedCommand) ||
            !checkExtraneousCharacterEntities(adjustedCommand) ||
            !checkExtraneousLocationEntities(adjustedCommand)) {
                return "Invalid command. Inv commands are not supposed to have any subjects.";
        }

        // Check if the inventory is empty
        HashMap<String, Artefact> inventory = currentPlayer.getInventory();
        if (inventory.isEmpty()) {
            return "You are empty handed.";
        }

        // Print the inventory
        StringBuilder inventoryInfo = new StringBuilder();
        for (Map.Entry<String, Artefact> entry : inventory.entrySet()) {
            inventoryInfo.append("- " + entry.getKey() + ": "+ entry.getValue().getDescription() + "\n");
        }
        inventoryInfo.deleteCharAt(inventoryInfo.length() - 1);
        return inventoryInfo.toString();
    }

    private String handleGet(String adjustedCommand) {
        // Get the command content after "get" to make sure the command is correct-ordered
        adjustedCommand = " " + getAfterSubstring(adjustedCommand.trim(), "get").trim() + " ";

        // Find the artefact to get and check if it is the only one
        String artefactToGet = null;
        for (Map.Entry<String, Artefact> entry : artefacts.entrySet()) {
            String artefactName = entry.getKey();
            String adjustedArtefactName = " " + artefactName + " ";
            if (adjustedCommand.contains(adjustedArtefactName)) {
                if (artefactToGet == null) {
                    artefactToGet = artefactName;
                } else {
                    return "Invalid command. Please get only one artefact at a time.";
                }
            }
        }

        // Check if there is an artefact to get found
        if (artefactToGet == null) {
            return "Invalid command. Please specify an artefact ro get.";
        }

        // Check extraneous entities
        if (!checkExtraneousFurnitureEntities(adjustedCommand) ||
            !checkExtraneousCharacterEntities(adjustedCommand) ||
            !checkExtraneousLocationEntities(adjustedCommand)) {
                return "Invalid command. Please provide a correct subject for the get command.";
        }

        // Check if the artefact is in the current location
        HashMap<String, Artefact> artefactsInCurrentLocation = currentPlayer.getLocation().getArtefacts();
        if (!artefactsInCurrentLocation.containsKey(artefactToGet)) {
            return "There is no " + artefactToGet + " here.";
        } else {
            Artefact artefact = artefactsInCurrentLocation.get(artefactToGet);
            currentPlayer.addArtefact(artefactToGet, artefact);
            currentPlayer.getLocation().removeArtefact(artefact);
            return "You have picked up the " + artefactToGet + ".";
        }
    }

    private String handleDrop(String adjustedCommand) {
        // Get the command content after "drop" to make sure the command is correct-ordered
        adjustedCommand = " " + getAfterSubstring(adjustedCommand.trim(), "drop").trim() + " ";

        // Find the artefact to drop and check if it is the only one
        String artefactToDrop = null;
        for (Map.Entry<String, Artefact> entry : artefacts.entrySet()) {
            String artefactName = entry.getKey();
            String adjustedArtefactName = " " + artefactName + " ";
            if (adjustedCommand.contains(adjustedArtefactName)) {
                if (artefactToDrop == null) {
                    artefactToDrop = artefactName;
                } else {
                    return "Invalid command. Please drop only one artefact at a time.";
                }
            }
        }

        // Check if there is an artefact to drop found
        if (artefactToDrop == null) {
            return "Invalid command. Please specify an artefact ro drop.";
        }

        // Check extraneous entities
        if (!checkExtraneousFurnitureEntities(adjustedCommand) ||
            !checkExtraneousCharacterEntities(adjustedCommand) ||
            !checkExtraneousLocationEntities(adjustedCommand)) {
                return "Invalid command. Please provide a correct subject for the drop command.";
        }

        // Check if the artefact is in the player's inventory
        HashMap<String, Artefact> artefactsInPlayersInventory = currentPlayer.getInventory();
        if (!artefactsInPlayersInventory.containsKey(artefactToDrop)) {
            return "There is no " + artefactToDrop + " in player's inventory.";
        } else {
            Artefact artefact = artefactsInPlayersInventory.get(artefactToDrop);
            currentPlayer.removeArtefact(artefact);
            currentPlayer.getLocation().addArtefact(artefact);
            return "You have put down the " + artefactToDrop + ".";
        }
    }

    private String handleGoto(String adjustedCommand) {
        // Get the command content after "goto" to make sure the command is correct-ordered
        adjustedCommand = " " + getAfterSubstring(adjustedCommand.trim(), "goto").trim() + " ";

        // Find the location to go and check if there is another location in the command
        String locationToGo = null;
        for (Map.Entry<String, Location> entry : locations.entrySet()) {
            String locationName = entry.getKey();
            String adjustedLocationName = " " + locationName + " ";
            if (adjustedCommand.contains(adjustedLocationName)) {
                if (locationToGo == null) {
                    locationToGo = locationName;
                } else {
                    return "Invalid command. Please provide only one location to go at a time.";
                }
            }
        }

        // Check if there is a location to go found
        if (locationToGo == null) {
            return "Invalid command. Please provide a location to go.";
        }

        // Check extraneous entities
        if (!checkExtraneousFurnitureEntities(adjustedCommand) ||
            !checkExtraneousCharacterEntities(adjustedCommand) ||
            !checkExtraneousArtefactEntities(adjustedCommand)) {
                return "Invalid command. Please provide a correct subject for the goto command.";
        }

        // Check if the player can go to the location
        if (!currentPlayer.getLocation().getPaths().containsKey(locationToGo)) {
            return "You cannot goto " + locationToGo + " from here.";
        } else {
            Location location = locations.get(locationToGo);
            currentPlayer.getLocation().removeCharacter(currentPlayer);
            currentPlayer.setLocation(location);
            location.addCharacter(currentPlayer);
            return "You have gone to " + locationToGo + ".";
        }
    }

    private String handleLook(String adjustedCommand) {
        // Check extraneous entities
        if (!checkExtraneousArtefactEntities(adjustedCommand) ||
            !checkExtraneousFurnitureEntities(adjustedCommand) ||
            !checkExtraneousCharacterEntities(adjustedCommand) ||
            !checkExtraneousLocationEntities(adjustedCommand)) {
                return "Invalid command. Inv commands are not supposed to have any subjects.";
        }

        StringBuilder lookInfo = new StringBuilder();

        // Location and its description
        lookInfo.append(currentPlayer.getLocation().getName() + "\n");
        lookInfo.append(currentPlayer.getLocation().getDescription() + "\n");

        // Artefacts and their descriptions
        HashMap<String, Artefact> artefacts = currentPlayer.getLocation().getArtefacts();
        if (!artefacts.isEmpty()) {
            lookInfo.append("You can see the listed artefacts:\n");
            for (Map.Entry<String, Artefact> entry : artefacts.entrySet()) {
                String name = entry.getKey();
                String description = entry.getValue().getDescription();
                lookInfo.append(" - " + name + ": " + description + "\n");
            }
        }

        // Furniture and its description
        HashMap<String, Furniture> furniture = currentPlayer.getLocation().getFurniture();
        if (!furniture.isEmpty()) {
            lookInfo.append("You can see the listed furniture:\n");
            for (Map.Entry<String, Furniture> entry : furniture.entrySet()) {
                String name = entry.getKey();
                String description = entry.getValue().getDescription();
                lookInfo.append(" - " + name + ": " + description + "\n");
            }
        }

        // Characters and their description
        HashMap<String, Character> characters = currentPlayer.getLocation().getCharacters();
        if (characters.size() > 1) {
            lookInfo.append("You can see the listed characters:\n");
            for (Map.Entry<String, Character> entry : characters.entrySet()) {
                String name = entry.getKey();
                if(name != currentPlayer.getName()) {
                    String description = entry.getValue().getDescription();
                    lookInfo.append(" - " + name + ": " + description + "\n");
                }
            }
        }

        // Paths
        HashMap<String, Location> paths = currentPlayer.getLocation().getPaths();
        if (paths.isEmpty()) {
            lookInfo.append("There is currently no path to any other locations.\n");
        } else {
            lookInfo.append("You can go to the listed location from here:\n");
            for (Map.Entry<String, Location> entry : paths.entrySet()) {
                lookInfo.append(" - " + entry.getKey() + "\n");
            }
        }

        lookInfo.deleteCharAt(lookInfo.length() - 1);
        return lookInfo.toString();
    }

    private String handleHealth(String adjustedCommand) {
        // Check extraneous entities
        if (!checkExtraneousArtefactEntities(adjustedCommand) ||
                !checkExtraneousFurnitureEntities(adjustedCommand) ||
                !checkExtraneousCharacterEntities(adjustedCommand) ||
                !checkExtraneousLocationEntities(adjustedCommand)) {
            return "Invalid command. Health commands are not supposed to have any subjects.";
        }
        return "Your current health level is " + currentPlayer.getLives() + ".";
    }

    private String handleAction(ArrayList<String> keyphraseFound, String adjustedCommand) {
        // Find all possible actions
        HashSet<GameAction> possibleActions = new HashSet<>();
        for (String keyphrase : keyphraseFound) {
            for (GameAction action : actions.get(keyphrase)) {
             possibleActions.add(action);
            }
        }

        // Find matched and partially matched actions
        ArrayList<GameAction> matchedActions = new ArrayList<>();
        ArrayList<GameAction> partialMatchedActions = new ArrayList<>();
        for (GameAction action : possibleActions) {
            boolean subjectsMatch = true;
            int matchedSubjects = 0;
            for (String subject : action.getSubjects()) {
                if (adjustedCommand.contains(" " + subject + " ")) {
                    matchedSubjects++;
                } else {
                    subjectsMatch = false;
                }
            }
            if (subjectsMatch) {
                matchedActions.add(action);
            } else if (matchedSubjects > 0 && matchedSubjects < action.getSubjects().size()) {
                partialMatchedActions.add(action);
            }
        }

        // Check if there is action found and if it is ambiguous
        if (matchedActions.size() == 0 && partialMatchedActions.size() == 1) {
            GameAction action = partialMatchedActions.get(0);
            // Check extraneous subjects
            return checkExtraneousSubjects(adjustedCommand, action);
        } else if (matchedActions.size() == 0) {
            return "Invalid command. Please provide corresponded subjects of the action.";
        } else if (matchedActions.size() > 1) {
            return "Invalid command. You action is ambiguous.";
        } else {
            GameAction action = matchedActions.get(0);
            // Check extraneous subjects
            return checkExtraneousSubjects(adjustedCommand, action);
        }
    }

    private String getAfterSubstring(String a, String b) {
        int bIndex = a.indexOf(b);
        if (bIndex != -1) {
            return a.substring(bIndex + b.length());
        } else {
            return "";
        }
    }

    private boolean checkExtraneousArtefactEntities(String adjustedCommand) {
        for (Map.Entry<String, Artefact> entry : artefacts.entrySet()) {
            String adjustedName = " " + entry.getKey() + " ";
            if (adjustedCommand.contains(adjustedName)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkExtraneousFurnitureEntities(String adjustedCommand) {
        for (Map.Entry<String, Furniture> entry : furniture.entrySet()) {
            String adjustedName = " " + entry.getKey() + " ";
            if (adjustedCommand.contains(adjustedName)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkExtraneousCharacterEntities(String adjustedCommand) {
        for (Map.Entry<String, Character> entry : characters.entrySet()) {
            String adjustedName = " " + entry.getKey() + " ";
            if (adjustedCommand.contains(adjustedName)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkExtraneousLocationEntities(String adjustedCommand) {
        for (Map.Entry<String, Location> entry : locations.entrySet()) {
            String adjustedName = " " + entry.getKey() + " ";
            if (adjustedCommand.contains(adjustedName)) {
                return false;
            }
        }
        return true;
    }

    private String checkExtraneousSubjects(String adjustedCommand, GameAction action) {
        String[] words = adjustedCommand.trim().split("\\s+");
        List<String> wordList = Arrays.asList(words);
        HashSet<String> restWordList= new HashSet<>(wordList);
        HashSet<String> keyphraseList = action.getTriggers();
        for (String keyphrase : keyphraseList) {
            String[] keyphraseWordsList = keyphrase.split("\\s+");
            for (String word : keyphraseWordsList) {
                restWordList.remove(word);
            }
        }
        for (String word : action.getSubjects()) {
            restWordList.remove(word);
        }
        if (checkExtraneousEntityWords(restWordList)) {
            return performAction(action);
        } else {
            return "Invalid command. You provided extraneous subjects.";
        }
    }

    private boolean checkExtraneousEntityWords(HashSet<String> wordList) {
        for (String word : wordList) {
            if (artefacts.containsKey(word)) {
                return false;
            }
            if (furniture.containsKey(word)) {
                return false;
            }
            if (characters.containsKey(word)) {
                return false;
            }
            if (locations.containsKey(word)) {
                return false;
            }
        }
        return true;
    }

    private String performAction(GameAction action) {
        // Check if subjects are available
        for (String subject : action.getSubjects()) {
            if (artefacts.containsKey(subject)) {
                if (!currentPlayer.getInventory().containsKey(subject) &&
                    !currentPlayer.getLocation().getArtefacts().containsKey(subject)) {
                        return "There is no " + subject + " in your inventory or current location.";
                }
            } else if (furniture.containsKey(subject)) {
                if (!currentPlayer.getLocation().getFurniture().containsKey(subject)) {
                    return "There is no" + subject + " in your current location.";
                }
            } else if (characters.containsKey(subject)) {
                if (!currentPlayer.getLocation().getCharacters().containsKey(subject)) {
                    return "There is no character called " + subject + " here.";
                }
            } else if (locations.containsKey(subject)) {
                if (!currentPlayer.getLocation().getName().equals(subject)) {
                    return "You are not in the " + subject + ".";
                }
            } else {
                return "Unknown error. Subject " + subject + " not found.";
            }
        }

        Location storeRoom = locations.get("storeroom");

        // Handle consumed
        for (String consumed : action.getConsumed()) {
            if (consumed.equals("health")) {
                currentPlayer.loseLife();
                // Check if the player is dead and reset its location
                if (currentPlayer.getLives() == 3) {
                    currentPlayer.getLocation().removeCharacter(currentPlayer);
                    currentPlayer.setLocation(startLocation);
                    startLocation.addCharacter(currentPlayer);
                    return "you died and lost all of your items, you must return to the start of the game";
                }
            } else if (artefacts.containsKey(consumed)) {
                Artefact consumedArtefact = artefacts.get(consumed);
                if (currentPlayer.getInventory().containsKey(consumed)) {
                    currentPlayer.removeArtefact(consumedArtefact);
                } else {
                    currentPlayer.getLocation().removeArtefact(consumedArtefact);
                }
                storeRoom.addArtefact(consumedArtefact);
            } else if (furniture.containsKey(consumed)) {
                Furniture consumedFurniture = furniture.get(consumed);
                currentPlayer.getLocation().removeFurniture(consumedFurniture);
                storeRoom.addFurniture(consumedFurniture);
            } else if (characters.containsKey(consumed)) {
                Character consumedCharacter = characters.get(consumed);
                currentPlayer.getLocation().removeCharacter(consumedCharacter);
                storeRoom.addCharacter(consumedCharacter);
            } else if (locations.containsKey(consumed)) {
                Location path = locations.get(consumed);
                currentPlayer.getLocation().removePath(path);
            } else {
                return "Unknown error. Consumed subject " + consumed + " not found.";
            }
        }

        // Handle produced
        for (String produced : action.getProduced()) {
            if (produced.equals("health")) {
                currentPlayer.addLife();
            } else if (artefacts.containsKey(produced)) {
                Artefact producedArtefact = artefacts.get(produced);
                findArtefactLocation(producedArtefact).removeArtefact(producedArtefact);
                currentPlayer.getLocation().addArtefact(producedArtefact);
            } else if (furniture.containsKey(produced)) {
                Furniture producedFurniture = furniture.get(produced);
                findFurnitureLocation(producedFurniture).removeFurniture(producedFurniture);
                currentPlayer.getLocation().addFurniture(producedFurniture);
            } else if (characters.containsKey(produced)) {
                Character producedCharacter = characters.get(produced);
                findCharacterLocation(producedCharacter).removeCharacter(producedCharacter);
                currentPlayer.getLocation().addCharacter(producedCharacter);
            } else if (locations.containsKey(produced)) {
                Location path = locations.get(produced);
                currentPlayer.getLocation().addPath(path);
            } else {
                return "Unknown error. Produced subject " + produced + " not found.";
            }
        }

        return action.getNarration();
    }

    private Location findArtefactLocation(Artefact artefact) {
        for (Map.Entry<String, Location> entry : locations.entrySet()) {
            Location location = entry.getValue();
            if (location.getArtefacts().containsKey(artefact.getName())) {
                return location;
            }
        }
        return locations.get("storeroom");
    }

    private Location findFurnitureLocation(Furniture furniture) {
        for (Map.Entry<String, Location> entry : locations.entrySet()) {
            Location location = entry.getValue();
            if (location.getFurniture().containsKey(furniture.getName())) {
                return location;
            }
        }
        return locations.get("storeroom");
    }

    private Location findCharacterLocation(Character character) {
        for (Map.Entry<String, Location> entry : locations.entrySet()) {
            Location location = entry.getValue();
            if (location.getCharacters().containsKey(character.getName())) {
                return location;
            }
        }
        return locations.get("storeroom");
    }

    //  === Methods below are there to facilitate server related operations. ===
    public void blockingListenOn(int portNumber) throws IOException {
        try (ServerSocket s = new ServerSocket(portNumber)) {
            System.out.println("Server listening on port " + portNumber);
            while (!Thread.interrupted()) {
                try {
                    blockingHandleConnection(s);
                } catch (IOException e) {
                    System.out.println("Connection closed");
                }
            }
        }
    }

    private void blockingHandleConnection(ServerSocket serverSocket) throws IOException {
        try (Socket s = serverSocket.accept();
        BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()))) {
            System.out.println("Connection established");
            String incomingCommand = reader.readLine();
            if(incomingCommand != null) {
                System.out.println("Received message from " + incomingCommand);
                String result = handleCommand(incomingCommand);
                writer.write(result);
                writer.write("\n" + END_OF_TRANSMISSION + "\n");
                writer.flush();
            }
        }
    }
}
