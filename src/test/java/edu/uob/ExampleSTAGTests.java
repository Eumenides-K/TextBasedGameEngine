package edu.uob;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Paths;
import java.io.IOException;
import java.time.Duration;

class ExampleSTAGTests {

  private GameServer server;

  // Create a new server _before_ every @Test
  @BeforeEach
  void setup() {
      File entitiesFile = Paths.get("config" + File.separator + "extended-entities.dot").toAbsolutePath().toFile();
      File actionsFile = Paths.get("config" + File.separator + "extended-actions.xml").toAbsolutePath().toFile();
      server = new GameServer(entitiesFile, actionsFile);
  }

  String sendCommandToServer(String command) {
      // Try to send a command to the server - this call will timeout if it takes too long (in case the server enters an infinite loop)
      return assertTimeoutPreemptively(Duration.ofMillis(1000), () -> { return server.handleCommand(command);},
      "Server took too long to respond (probably stuck in an infinite loop)");
  }

  // A lot of tests will probably check the game state using 'look' - so we better make sure 'look' works well !
  @Test
  void testLook() {
    String response = sendCommandToServer("simon: look");
    response = response.toLowerCase();
    assertTrue(response.contains("cabin"), "Did not see the name of the current room in response to look");
    assertTrue(response.contains("log cabin"), "Did not see a description of the room in response to look");
    assertTrue(response.contains("magic potion"), "Did not see a description of artifacts in response to look");
    assertTrue(response.contains("wooden trapdoor"), "Did not see description of furniture in response to look");
    assertTrue(response.contains("forest"), "Did not see available paths in response to look");
  }

  // Test that we can pick something up and that it appears in our inventory
  @Test
  void testGet()
  {
      String response;
      sendCommandToServer("simon: get potion");
      response = sendCommandToServer("simon: inv");
      response = response.toLowerCase();
      assertTrue(response.contains("potion"), "Did not see the potion in the inventory after an attempt was made to get it");
      response = sendCommandToServer("simon: look");
      response = response.toLowerCase();
      assertFalse(response.contains("potion"), "Potion is still present in the room after an attempt was made to get it");
  }

  // Test that we can goto a different location (we won't get very far if we can't move around the game !)
  @Test
  void testGoto()
  {
      sendCommandToServer("simon: goto forest");
      String response = sendCommandToServer("simon: look");
      response = response.toLowerCase();
      assertTrue(response.contains("key"), "Failed attempt to use 'goto' command to move to the forest - there is no key in the current location");
  }

  // Add more unit tests or integration tests here.
  @Test
  void testInvalidUserName()
  {
      String response = sendCommandToServer("si!mon: goto cabin").toLowerCase();
      assertTrue(response.contains("invalid"), "Failed to detect invalid username");
  }

  @Test
  void testActionsAndHealth()
  {
      // Test case Insensitivity
      sendCommandToServer("simon: GotO FoResT");
      assertTrue(sendCommandToServer("simon: look").toLowerCase().contains("key"), "Command is not case insensitive.");
      assertTrue(sendCommandToServer("SIMoN: look").toLowerCase().contains("key"), "Username is not case insensitive.");

      sendCommandToServer("simon: goto cabin");
      sendCommandToServer("simon: get potion");
      sendCommandToServer("simon: get axe");
      sendCommandToServer("simon: get coin");
      sendCommandToServer("simon: goto forest");
      sendCommandToServer("simon: get key");
      sendCommandToServer("simon: goto cabin");

      // Test invalid command
      assertTrue(sendCommandToServer("simon: goto open").toLowerCase().contains("invalid"), "Failed to detect invalid command");
      assertTrue(sendCommandToServer("simon: forest goto").toLowerCase().contains("invalid"), "Failed to detect invalid command");
      assertTrue(sendCommandToServer("simon: ket drop").toLowerCase().contains("invalid"), "Failed to detect invalid command");
      assertTrue(sendCommandToServer("simon: key get").toLowerCase().contains("invalid"), "Failed to detect invalid command");
      assertTrue(sendCommandToServer("simon: open").toLowerCase().contains("invalid"), "Failed to detect invalid command");
      assertTrue(sendCommandToServer("simon: goto open").toLowerCase().contains("invalid"), "Failed to detect invalid command");
      assertTrue(sendCommandToServer("simon: oopen trapdoor with key").toLowerCase().contains("invalid"), "Failed to detect invalid command");
      assertTrue(sendCommandToServer("simon: open trapdoor potion").toLowerCase().contains("invalid"), "Failed to detect invalid command");

      // Test valid command
      sendCommandToServer("simon: open the trapdoor with key");
      assertTrue(sendCommandToServer("simon: look").toLowerCase().contains("cellar"), "Failed to do open trapdoor action");

      // Test health
      sendCommandToServer("simon: goto cellar");
      sendCommandToServer("simon: fight elf");
      assertTrue(sendCommandToServer("simon: health").contains("2"), "Failed to reduce health level.");
      sendCommandToServer("simon: drink potion");
      assertTrue(sendCommandToServer("simon: health").contains("3"), "Failed to increase health level");

      // Test death
      sendCommandToServer("simon: fight elf");
      sendCommandToServer("simon: fight elf");
      sendCommandToServer("simon: fight elf");
      assertTrue(sendCommandToServer("simon: look").toLowerCase().contains("trapdoor"), "Failed to reset the dead player.");
      assertFalse(sendCommandToServer("simon: inv").toLowerCase().contains("axe"), "Failed to reset the dead player.");
      sendCommandToServer("simon: goto cellar");
      assertTrue(sendCommandToServer("simon: look").toLowerCase().contains("axe"), "Failed to reset the dead player.");
      sendCommandToServer("simon: get axe");

      // Test composite actions
      sendCommandToServer("simon: fight elf and pay elf coin");
      assertFalse(sendCommandToServer("simon: look").toLowerCase().contains("shovel"), "Failed to do detect composite actions.");

      // Test partial action and produced
      sendCommandToServer("simon: pay elf");
      assertTrue(sendCommandToServer("simon: look").toLowerCase().contains("shovel"), "Failed to do partial action of pay elf or failed to produce shovel.");

      sendCommandToServer("simon: get shovel");
      sendCommandToServer("simon: goto cabin");
      sendCommandToServer("simon: goto forest");
      sendCommandToServer("simon: cut tree");
      sendCommandToServer("simon: get log");
      sendCommandToServer("simon: goto riverbank");

      // Test word ordering
      sendCommandToServer("simon: horn blow");
      assertTrue(sendCommandToServer("simon: look").toLowerCase().contains("lumberjack"), "Failed to blow the horn by abnormal word ordering.");
      sendCommandToServer("simon: bridge the river");
      sendCommandToServer("simon: goto clearing");

      // Test consumed
      assertTrue(sendCommandToServer("simon: look").toLowerCase().contains("looks like the soil has been recently disturbed"), "Failed to consume ground.");
      sendCommandToServer("simon: dig ground");
      assertFalse(sendCommandToServer("simon: look").toLowerCase().contains("looks like the soil has been recently disturbed"), "Failed to consume ground.");

      // Test drop
      sendCommandToServer("simon: get gold");
      sendCommandToServer("simon: goto riverbank");
      sendCommandToServer("simon: drop gold");
      assertTrue(sendCommandToServer("simon: look").toLowerCase().contains("gold"), "Failed to drop artefact.");
  }

    @Test
    void testMultiPlayer()
    {
        String response = sendCommandToServer("simon: look");
        assertTrue(!response.contains("john"), "Multiplayer error.");

        response = sendCommandToServer("john: look");
        assertTrue(response.contains("simon"), "Multiplayer error.");

        response = sendCommandToServer("simon: look");
        assertTrue(response.contains("john"), "Multiplayer error.");

        sendCommandToServer("simon: goto forest");
        response = sendCommandToServer("john: look");
        assertTrue(!response.contains("simon"), "Multiplayer error.");

        // Test invalid username
        response = sendCommandToServer("si!mon: look");
        assertTrue(!response.contains("cabin"), "Multiplayer error.");
    }

    @Test
    void testMultipleTriggers()
    {
        sendCommandToServer("simon: goto forest");
        sendCommandToServer("simon: get key");
        sendCommandToServer("simon: goto cabin");
        sendCommandToServer("simon: unlock key");
        sendCommandToServer("simon: goto cellar");
        sendCommandToServer("simon: fight and attack elf");
        assertTrue(sendCommandToServer("simon: health").contains("2"), "Failed to reduce health level.");
    }
  @Test
  void extraTest()
  {
    // Test handle entities file without storeroom
    File entitiesFile = Paths.get("config" + File.separator + "extended-entities-without-storeroom.dot").toAbsolutePath().toFile();
    File actionsFile = Paths.get("config" + File.separator + "extended-actions-test.xml").toAbsolutePath().toFile();
    server = new GameServer(entitiesFile, actionsFile);
    sendCommandToServer("simon: goto forest");
    sendCommandToServer("simon: get key");
    sendCommandToServer("simon: goto cabin");
    sendCommandToServer("simon: open the trapdoor with key");
    sendCommandToServer("simon: goto storeroom");
    assertTrue(sendCommandToServer("simon: look").toLowerCase().contains("storeroom"), "Failed to create built-in storeroom.");

    // Test remove path
    Location location = new Location("test", "test");
    Location location1 = new Location("path", "path");
    location.addPath(location1);
    assertTrue(location.getPaths().containsKey("path"), "Failed to add path.");
    location.removePath(location1);
    assertFalse(location.getPaths().containsKey("path"), "Failed to remove path.");
  }
}

