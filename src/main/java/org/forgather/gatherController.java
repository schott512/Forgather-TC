/*
 * Copyright (c) 2017 schott512
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the conditions found in the LICENSE file.
 *
 */

package org.forgather;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Screen;
import org.sqlite.DatabaseManager;
import io.magicthegathering.javasdk.api.*;
import io.magicthegathering.javasdk.resource.*;
import javafx.geometry.Rectangle2D;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.File;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * A class which will act as the controller for the Forgather software. This class will take requests from
 * the User Interface and perform all queries to the sqlite database as well as all interactions with the
 * magicthegathering api.
 */
public class gatherController {

    // ~~~~~~Class Variables~~~~~~~
    private static DatabaseManager localDB = null;
    private static iViewController imageViewerWindow = null;
    private static Stage iView = null;
    private static PrintWriter logFile = null;
    private static final String[] tabList = {"about", "add", "view", "decks"};
    private int selectedTab = 0;
    // ~~~~~End Class Variables~~~~

    /**
     * Initializes window for viewing card images, as well as a localDB manager
     */
    public gatherController(){

        // Grab current data/time and format it into a string
        java.util.Date date = Calendar.getInstance().getTime();
        Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String logDate = formatter.format(date);

        // set iv to the controller for companion window
        imageViewerWindow = new iViewController();

        // Open log file
        try {
            String s = File.separator;
            String path = System.getProperty("user.dir")+ s + "logs" + s + "app-log.txt";
            FileWriter fw = new FileWriter(path, true);
            BufferedWriter bw = new BufferedWriter(fw);
            logFile = new PrintWriter(bw);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Start the log entry with data and time
        log( "========================================" + logDate + "========================================",
                true, true);

        try {
            // create new window for image viewing
            iView = new Stage();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/FXML/imageViewer.fxml"));
            loader.setController(imageViewerWindow);
            Parent root = loader.load();
            Scene view = new Scene(root);
            iView.setTitle("Card View");
            iView.setScene(view);
        }
        catch (IOException e) {
            log(e.getMessage(), false, true);
            System.exit(3);
        }

        // create a DatabaseManager to handle localDB calls
        localDB = new DatabaseManager();
    }

    /**
     * Writes string input to file.
     *
     * @param s String to be logged
     * @param pre Boolean indicating if the user desires a new line at the beginning
     * @param post Boolean indicating if the user desires a new line at the end
     */
    private static void log(String s, Boolean pre, Boolean post) {

        if (pre) { logFile.println("\n"); }
        logFile.print(s);
        if (post) { logFile.println("\n"); }

    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~BEGIN API INTERACTIONS CODE~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Returns all cards matching the filters parameters.
     *
     * @param filters List of String formatted as "filtername='datavalue'". See MTGAPI Documentation for valid filters.
     * @return A list of card objects which match the filter parameters.
     */
    private static List<Card> searchCards(List<String> filters) {

        // Run API query, return results
        return CardAPI.getAllCards(filters);

    }

    /**
     * Uses ID to find card in MTG API
     *
     * @param id A string denoting the card ID to use
     * @return A card object from mtgAPI
     */
    private static Card cardByID(String id) {

        // Use API to grab card by ID
        return CardAPI.getCard(id);

    }

    /**
     * Use card number to find a card in MTG API
     *
     * @param n Card number of card
     * @param s Set card is in, requires both n and s to find card
     * @return The card with the specified number in set
     */
    private static Card cardByNum(String n, String s) {

        List<String> filters = new ArrayList<>();
        filters.add("set="+s);
        filters.add("number="+n);

        // Return first result of search, as any N + S pair should identify only 1 card
        System.out.println(searchCards(filters).size());
        return searchCards(filters).get(0);

    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~END API INTERACTIONS CODE~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~BEGIN localDB INTERACTIONS CODE~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Adds a card to the database.
     *
     * @param c Card object to be interpreted and written to localDB
     */
    private void addToCollection(Card c) {

        // Check necessary attributes and set them if null
        String[] l = {"None"};
        if (c.getSubtypes() == null) {
            c.setSubtypes(l);
        }
        if (c.getSupertypes() == null) {
            c.setSupertypes(l);
        }
        if (c.getColors() == null) {
            c.setColors(l);
        }
        if (c.getText() == null) { c.setText(""); }

        // Map Card attributes to list of objects that will compose the table entry
        Object[] objects = {c.getLayout(), c.getName().replace('\'', '"'), c.getId(), String.join("", c.getColors()), c.getImageUrl(),
                c.getText().replace('\'', '"'), c.getType(), String.join(", ", c.getSubtypes()),
                String.join(", ", c.getSupertypes()),c.getManaCost(), c.getCmc(), c.getPower(),
                c.getToughness(), c.getLoyalty(), c.getSet()};

        // Insert into Card table
        localDB.addTo("CARDS", "(layout, name, id, color, image, card_text, type, subtype, supertype, " +
                "mana_cost, cmc, power, toughness, loyalty, set_id)", objects, false);

        // Create entry in Card_to_Deck table for this card and collection
        Object[] o = {c.getId(), -1, 1};
        localDB.addTo("CARD_TO_DECK", "(card_id, deck_id, copies)", o, false);

    }

    /**
     * Adds cards which have a 3-way (meld) relationship to database. At the time of writing only 3 meld pairs
     * exist. Assumes layout is meld.
     *
     * @param c A meld layout card
     */
    private void addMeld(Card c) {

        // Grab all names associated with this card (should be 3 because it is a meld card)
        List<String> n = Arrays.asList(c.getNames());

        // Remove c from list, as we already have its data
        List<String> others = new ArrayList<>(n);
        others.remove(others.indexOf(c.getName()));

        // Grab the other two cards in the relationship
        List<String> f1 = new ArrayList<>();
        f1.add("name="+others.get(0));
        List<String> f2 = new ArrayList<>();
        f2.add(("name=" + others.get(1)));

        Card c2 = searchCards(f1).get(0);
        Card c3 = searchCards(f2).get(0);

        // Add all individual cards
        addToCollection(c);
        addToCollection(c2);
        addToCollection(c3);

        // Find out which card is the combined meld, (last item in n should be melded card)
        // Create relationship accordingly (c3 is primary key, which should be combined card)
        // where as c1 and c2 can be in any order;
        String meldName = n.get(2);
        if (c.getName().equals(meldName)) {
            Object[] o = {c2.getId(), c3.getId(), c.getId(), c.getImageUrl()};
            localDB.addTo("Meld", "(card1, card2, card3, image)", o, true);
        }
        else if (c2.getName().equals(meldName)) {
            Object[] o = {c.getId(), c3.getId(), c2.getId(), c.getImageUrl()};
            localDB.addTo("Meld", "(card1, card2, card3, image)", o, true);
        }
        else {
            Object[] o = {c2.getId(), c.getId(), c3.getId(), c.getImageUrl()};
            localDB.addTo("Meld", "(card1, card2, card3, image)", o, true);
        }
    }

    /**
     * Adds cards which have some special relationship to the Database. Such as double-faced, split/aftermath,
     * or flip cards. Adds constituent cards to database one at a time and enters relationship into the appropriate
     * table. FLIPSPLIT_CARDS or DOUBLE_FACED. Example: C1 = "Breaking" c2 = "Entering" to create the aftermath/split
     * card Breaking and Entering.
     *
     * @param c1 First card in relationship.
     * @param c2 Second card in relationship.
     */
    private void addDoubleFlipSplit(Card c1, Card c2) {

        // Add individual cards
        addToCollection(c1);
        addToCollection(c2);

        // Check which relationship it should be added to, then add
        if (c1.getLayout().equals("flip") || c1.getLayout().equals("split") || c1.getLayout().equals("aftermath")) {

            // Map attributes to list of objects to be added
            Object[] o = {c1.getId(), c2.getId(), c1.getImageUrl()};

            // Add
            localDB.addTo("FLIPSPLIT_CARDS", "(card1, card2, image)", o, true);
        } else {

            // Map attributes to list of objects to be added
            Object[] o = {c1.getId(), c2.getId(), c1.getImageUrl()};

            // Add
            localDB.addTo("DOUBLE_FACED", "(card1, card2, image)", o, true);

        }

    }

    /**
     * Removes a card from the personal DataBase. If it is part of a relationship, both are removed.
     *
     * @param id     A string representing a card ID
     * @param layout A string representing the cards layout, so that we can determine whether it is part
     *               of a relationship
     */
    private void removeCardFromDB(String id, String layout) {

        // Find out if there is a relationship between cards
        String wh;
        String other;
        List<Map<String, Object>> result;

        switch (layout) {

            case "flip":
            case "split":
            case "aftermath":

                // Grab the relationship
                wh = "card1=='" + id + "' OR " + "card2=='" + id + "'";
                result = localDB.searchDB("card1, card2", "FLIPSPLIT_CARDS", wh, "",
                        1, "", "");

                if (result.get(0).get("card1").toString().equals(id)) {other = result.get(0).get("card2").toString();}
                else {other = result.get(0).get("card1").toString();}

                // Remove relationship
                localDB.removeFrom("FLIPSPLIT_CARDS", wh);

                // Remove card from any decks
                localDB.removeFrom("CARD_TO_DECK", "card_id=='" + other +"'");

                // Finally, remove card with id
                localDB.removeFrom("CARDS", "id=='"+other+"'");
                break;

            case "double-faced":

                // Grab the relationship
                wh = "card1=='" + id + "' OR " + "card2=='" + id + "'";
                result = localDB.searchDB("card1, card2", "DOUBLE_FACED", wh, "",
                        1, "", "");

                // Remove other card
                if (result.get(0).get("card1").toString().equals(id)) {other = result.get(0).get("card2").toString();}
                else { other = result.get(0).get("card1").toString();}

                // Remove relationship
                localDB.removeFrom("DOUBLE_FACED", wh);

                // Remove card from any decks
                localDB.removeFrom("CARD_TO_DECK", "card_id=='" + other + "'");

                // Finally, remove card with id
                localDB.removeFrom("CARDS", "id=='"+ other +"'");
                break;

            case "meld":
                // Grab the relationship
                wh = "card1=='" + id + "' OR " + "card2=='" + id + "' OR " + "card3=='" + id + "'";
                result = localDB.searchDB("card1, card2, card3", "MELD", wh, "",
                        1, "", "");

                // Remove other cards
                String other2;
                if (result.get(0).get("card1").toString().equals(id)) {
                    other = result.get(0).get("card2").toString();
                    other2 = result.get(0).get("card3").toString();
                }
                else if (result.get(0).get("card2").toString().equals(id)){
                    other = result.get(0).get("card1").toString();
                    other2 = result.get(0).get("card3").toString();
                }
                else {
                    other = result.get(0).get("card1").toString();
                    other2 = result.get(0).get("card2").toString();
                }

                // Remove relationship
                localDB.removeFrom("meld", wh);

                // Remove cards from any decks
                localDB.removeFrom("CARD_TO_DECK", "card_id=='" + other + "'");
                localDB.removeFrom("CARD_TO_DECK", "card_id=='" + other2 + "'");

                // Finally, remove card with id
                localDB.removeFrom("CARDS", "id=='"+ other +"'");
                localDB.removeFrom("CARDS", "id=='"+ other2 +"'");
                break;

        }

        // Remove this card from any decks
        localDB.removeFrom("CARD_TO_DECK", "card_id=='" + id +"'");

        // Finally, remove card with id
        localDB.removeFrom("CARDS", "id=='"+id+"'");

    }

    /**
     * Adds a deck to the database.
     *
     * @param color String representing the colors the deck contains.
     * @param count Number of cards that this deck contains.
     * @param name  String representing what the user refers to the deck as.
     * @param box   ID of box this deck is in.
     */
    private void addDeck(String color, int count, String name, int box) {

        // Build the list of things to add, add deck data to localDB
        Object[] obj = {color, count, name.replace('\'', '"'), box};
        localDB.addTo("DECKS", "(color, card_count, name, box_id)", obj, false);

    }


    /**
     * Imports all sets the MTGAPI contains.
     */
    private void importSets() {

        // Grab all sets
        List<MtgSet> s = SetAPI.getAllSets();

        // Cycle through sets and add them to database
        for (MtgSet set : s) {

            // Create list of attributes of set to add
            if (set.getBlock() == null) {
                set.setBlock("");
            }
            Object[] o = {set.getCode(), set.getName().replace('\'', '"'), set.getReleaseDate(), set.getBlock().replace('\'', '"')};
            localDB.addTo("SETS", "(id, name, release, block)", o, true);
        }

    }

    /**
     * Adds a box (physical location) to the localDB.
     *
     * @param name String representing name of box
     * @param loc  String representing description of physical location
     */
    private void addBox(String name, String loc) {

        // Build list of attributes and add box

        Object[] box = {name, loc};
        localDB.addTo("BOXES", "(name, location)", box, false);
    }

    /**
     *
     * Grabs a card from the database using its ID
     *
     *  @param id A string representing a card's ID
     */

    private Map<String, Object> DBCardByID(String id) {

        return localDB.searchDB("name, id, set_id, color, type, layout, image","CARDS","id=='" + id + "'",
                "",1,"","").get(0);

    }

    /**
     * Searches for cards from the localDB
     *
     * @param layout   Layout, specifies what type of card layout to get. Possible values are split, double-faced, aftermath, flip
     *                 normal, or empty string; Empty string indicates all cards. When "normal", will search for all cards
     *                 not having a split, flip, double-faced, or aftermath layout
     *                 because MTGAPI defines more than 5 layouts.
     * @param name     String representing name. Cards including the sting in their name will be grabbed.
     * @param colors   String representing the color identities of a card.
     * @param set      String indicating set_id such as "SOI" for Shadows over Innistrad
     * @param type     String indicating card type
     * @param subtype  String indicating a card's subtype
     * @param pwr      String containing integer power value or "", indicating any
     * @param tgh      String containing integer toughness value or "", indicating any
     * @param loyalty  String containing integer loyalty value or "", indicating any
     * @param cmc      String containing integer converted mana cost or "", indicating any
     * @param lim      Integer indicating maximum number of returned values
     * @return  A list of Map objects. Each map object represents a row, with keys being "name, id, set_id, color" for
     *          each card in the list
     */
    private List<Map<String, Object>> cardsFromDB(String layout, String name, String colors, String set,
                                                        String type, String subtype, String supertype, String pwr,
                                                        String tgh, String loyalty,String cmc,
                                                        int lim) {

        // Create a list of strings to store where clause arguments
        List<String> wh = new ArrayList<>();

        // Create a from string and a sel string telling which attributes to select
        String fr = "CARDS";
        String sel = "name, id, set_id, color, type, layout";

        // A list of all layouts which belong to the FLIPSPLIT_CARDS table
        List<String> flipSplitLayouts = Arrays.asList("flip", "split", "aftermath");

        // Check layout string, and create appropriate select and from statements. Add appropriate where clauses.
        if (flipSplitLayouts.contains(layout)) {
            wh.add("c1.layout IN ('split', 'aftermath', 'flip')");
            wh.add("c1.id == f.card1");
            wh.add("c2.id == f.card2");
            fr = "CARDS c1, CARDS c2, FLIPSPLIT_CARDS f";
            sel = "c1.name || '//' || c2.name as name, c1.id as Card1, c2.id as Card2, c1.set_id, c1.color";
        } else if (layout.equals("double-faced")) {
            wh.add("c1.layout == 'double-faced'");
            wh.add("c1.id == f.card1");
            wh.add("c2.id == f.card2");
            fr = "CARDS c1, CARDS c2, DOUBLE_FACED d";
            sel = "c1.name || '//' || c2.name as name, c1.id as Card1, c2.id as Card2, c1.set_id, c1.color";
        } else if (layout.equals("Normal")) {
            wh.add("layout not in ('aftermath', 'split', 'flip', 'double-faced')");
        }
        else if (layout.equals("meld")) {wh.add("layout == 'meld'");}

        // Add optional where clauses
        if (!name.equals("")) {
            wh.add("name LIKE '%" + name + "%'");
        }
        if (!colors.equals("")) {
            wh.add("color=='" + colors + "'");
        }
        if (!set.equals("")) {
            wh.add("set_id=='" + set + "'");
        }
        if (!type.equals("")) {
            wh.add("type=='" + type + "'");
        }
        if (!subtype.equals("")) {
            wh.add("subtype=='" + subtype + "'");
        }
        if (!supertype.equals("")) {
            wh.add("supertype=='" + supertype + "'");
        }
        if (!pwr.equals("")) {
            wh.add("power==" + pwr);
        }
        if (!tgh.equals("")) {
            wh.add("toughness==" + tgh);
        }
        if (!loyalty.equals("")) {
            wh.add("loyalty==" + loyalty);
        }
        if (!cmc.equals("")) {
            wh.add("cmc==" + cmc);
        }

        // Join all where clauses, separated by " AND "
        String whe = String.join(" AND ", wh);

        // Run search with built up parameters
        return localDB.searchDB(sel, fr, whe, "name", lim, "", "");
    }

    /**
     * Fetches sets from the database.
     *
     * @param name  String representing name, any set with a partial match will be returned.
     * @param block String representing block, any set with a partial match will be returned.
     * @param lim   Integer effecting maximum number of sets to retrieve
     * @return A list of Map objects. Each map is one row. Keys are "name", "id", "block", "release"
     */
    public List<Map<String, Object>> setsFromlocalDB(String name, String block, int lim) {

        // Empty list of where clauses
        List<String> wh = new ArrayList<>();

        // Build up where clause
        if (!name.equals("")) {
            wh.add("name LIKE '%" + name + "%'");
        }
        if (!block.equals("")) {
            wh.add("block LIKE '%" + block + "%'");
        }

        // Join where clauses with " AND "
        String whe = String.join(" AND ", wh);

        // Return result of localDB search
        return localDB.searchDB("name, id, block, release", "SETS", whe, "name", lim, "", "");
    }

    /**
     * Searches for all decks satisfying input parameters
     *
     * @param name     A string representing user-defined Deck Name
     * @param location A string representing user-defined Deck Location (box)
     * @param color    A string indicating deck color(s)
     * @param lim      An int indicating maximum number of results to return
     * @return A List of Map objects. Each map is one row with keys "name", "id", "color", "count", "location", "box_id", "bname".
     */
    public List<Map<String, Object>> getDecks(String name, String location, String color, int lim) {

        // Empty list of where clauses
        List<String> wh = new ArrayList<>();

        // Clause that joins tables along box id
        wh.add("d.box_id==b.id");

        // Name is optional search clause
        if (!name.equals("")) {
            wh.add("name LIKE '%" + name + "%'");
        }

        // Location is optional
        if (!location.equals("")) {
            wh.add("location LIKE '%" + location + "%'");
        }

        // Color is optional
        if (!color.equals("")) { wh.add("color LIKE '%" + color + "%'"); }


        // Join where clauses
        String whe = String.join(" AND ", wh);

        return localDB.searchDB("d.name as name, id, color, card_count as count, b.location as location, id as box_id, b.name as bname",
                "DECKS d, BOXES b", whe, "name", lim, "", "");
    }

    /**
     * Searches for all decks satisfying input parameters
     *
     * @param name     A string representing user-defined Deck Name
     * @param location A string representing user-defined Deck Location (box)
     * @param lim      An int indicating maximum number of results to return
     * @return A list of Map objects. Each map is a row with keys ('id', 'name', 'location')
     */
    public List<Map<String, Object>> getBoxes(String name, String location, int lim) {

        // Empty list of where clauses
        List<String> wh = new ArrayList<>();

        // Name is optional search clause
        if (!name.equals("")) {
            wh.add("name LIKE '%" + name + "%'");
        }

        // Location is optional
        if (!location.equals("")) {
            wh.add("location LIKE '%" + location + "%'");
        }

        String whe = String.join(" AND ", wh);

        return localDB.searchDB("id, name, location", "BOXES", whe, "name", lim, "", "");
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~END localDB INTERACTIONS CODE~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~BEGIN FXML PROCESSING CODE~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    @FXML private TabPane tabPane;
    @FXML private void tabManager() {

        // Grab index of pane selected
        int s = tabPane.getSelectionModel().getSelectedIndex();

        // If value is not the previously selected tab, update selectedTab and call tab init if necessary
        if (s != selectedTab) {

            String t = tabList[s];
            switch (t) {
                case "about":
                    selectedTab = 0;
                    break;

                case "add":
                    selectedTab = 1;
                    break;

                case "view":
                    selectedTab = 2;
                    vtabInit();
                    break;

                case "decks":
                    selectedTab = 3;
                    dTabInit();
                    break;
            }
        }
    }

    //----------------------------------------Add Cards Tab-------------------------------------------

    // Declare all necessary FXML fields for Adding Cards

    @FXML private TextField nameAC;
    @FXML private TextField setAC;
    @FXML private TextField typeAC;
    @FXML private TextField superAC;
    @FXML private TextField subAC;
    @FXML private TextField costAC;
    @FXML private TextField colorsAC;
    @FXML private TextField pwrAC;
    @FXML private TextField tghAC;
    @FXML private TextField loyaltyAC;
    @FXML private TextField rarityAC;
    @FXML private ListView cardList;
    @FXML private Label ACLabel;

    /**
     * Adds a card selected from cardList to the database
     *
     */
    @FXML protected void cAdd() {

        SelectionModel m = cardList.getSelectionModel();
        Object oid = m.getSelectedItem();

        // If nothing is selected, alert the user
        if (m.isEmpty()) {
            ACLabel.setText("Please select an item");
        }

        else {
            // Grab the ID field from the listed card, and grab from API
            String[] idfield = oid.toString().split("\n");
            String id = idfield[6].substring(4);
            Card c = cardByID(id);

            // If card involves a relation between two cards, get the other cards number
            if (c.getLayout().equals("double-faced") || c.getLayout().equals("flip") || c.getLayout().equals("split")
                    || c.getLayout().equals("aftermath")) {
                String num = c.getNumber();

                // Get both parts of the number (number and ending a or b)
                String n = num.substring(0, num.length() - 1);
                String l = num.substring(num.length() - 1);

                // Set l to its opposite
                if (l.equals("a")) {
                    l = "b";
                } else if (l.equals("b")) {
                    l = "a";
                }

                // Grab set card comes from for search
                String set = c.getSet();

                // Grab other card by searching by number (recombine n and l into a full card number)
                Card other = cardByNum(n + l, set);

                // Add card and its companion
                addDoubleFlipSplit(c, other);

                ACLabel.setText(c.getName() + " // " + other.getName() + " added");
            }

            // If the card is a meld, pass to addMeld
            else if (c.getLayout().equals("meld")) {
                addMeld(c);
            }

            // Otherwise, just add card
            else {
                addToCollection(c);
                ACLabel.setText(c.getName() + " Added");
            }
        }
        log("Add ran; " + ACLabel.getText(),false ,true);
    }

    /**
     * Uses parameters from UI TextFields to search MTG API for cards and then displays matching results
     * to the cardList ListView object
     *
     * @param e A JavaFX ActionEvent object
     */
    @FXML protected void cAddSearch(ActionEvent e) {

        // Begin parsing arguments list for searchCards
        // All parameters are optional, so check each one and add to list of args
        ArrayList<String> s = new ArrayList<>();

        // Also, set label to default value
        ACLabel.setText("Search for Cards");

        // Any parameters that are not empty should be added to s, empty ones can be ignored
        // There's probably a much cleaner way to do this, but for now this will have to do
        /* TO DO: Clean up this code */
        if (!nameAC.getText().isEmpty()) {
            s.add("name=" + nameAC.getText());
        }
        if (!setAC.getText().isEmpty()) {
            s.add("set=" + setAC.getText());
        }
        if (!typeAC.getText().isEmpty()) {
            s.add("type=" + typeAC.getText());
        }
        if (!superAC.getText().isEmpty()) {
            s.add("supertype=" + superAC.getText());
        }
        if (!subAC.getText().isEmpty()) {
            s.add("subtype=" + subAC.getText());
        }
        if (!costAC.getText().isEmpty()) {
            s.add("cmc=" + costAC.getText());
        }
        if (!colorsAC.getText().isEmpty()) {
            String[] c = colorsAC.getText().split(", ");
            String filter = "colors=";

            for (int i = 0; i < c.length; i++) {
                filter = filter + c[i];
                if (i != c.length - 1) {
                    filter = filter + ",";
                }
            }

            s.add(filter);
        }
        if (!pwrAC.getText().isEmpty()) {
            s.add("power=" + pwrAC.getText());
        }
        if (!tghAC.getText().isEmpty()) {
            s.add("toughness=" + tghAC.getText());
        }
        if (!loyaltyAC.getText().isEmpty()) {
            s.add("loyalty=" + loyaltyAC.getText());
        }
        if (!rarityAC.getText().isEmpty()) {
            s.add("rarity=" + rarityAC.getText());
        }

        // Query API
        List<Card> clist = searchCards(s);

        // If nothing, search failed. Otherwise send card list to listView
        if (clist.size() == 0) {ACLabel.setText("Search Failed!");}
        else {
            // Create a string representation for each card, add to an observable
            // list which will then be given to listView
            ObservableList<String> toListView = FXCollections.observableArrayList();
            for (Card c : clist) {
                List<String> col = new ArrayList<>();
                if (c.getColors() != null) {col = Arrays.asList(c.getColors());}
                String repr = "\nName: " + c.getName() + "\nSet: " + c.getSetName() +
                        "\nLayout: " + c.getLayout() + "\nCard#: " + c.getNumber() + "\nColors: " + String.join(", ", col) +
                        "\nID: " + c.getId();
                toListView.add(repr);
            }
            cardList.setItems(toListView);
        }
        log("AddSearch Ran" + ACLabel.getText(), false, true);
    }

    //---------------------------------------View Cards Tab-------------------------------------------

    @FXML private TextField nameMC;
    @FXML private TextField setMC;
    @FXML private TextField colorsMC;
    @FXML private TextField typeMC;
    @FXML private TextField subMC;
    @FXML private TextField superMC;
    @FXML private TextField pwrMC;
    @FXML private TextField tghMC;
    @FXML private TextField loyaltyMC;
    @FXML private TextField cmcMC;
    @FXML private TextField layoutMC;
    @FXML private ListView listMC;

    @FXML private void vtabInit() {

        // show image viewer
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        iView.setX(bounds.getMinX());
        iView.setY((bounds.getMaxY()-iView.getHeight())/2);
        iView.show();
        imageViewerWindow.reset();

    }

    @FXML private void searchMyCards() {

        // Pass parameters to cardsFromDB
        List<Map<String, Object>> result = cardsFromDB(layoutMC.getText(), nameMC.getText(), colorsMC.getText(),
                setMC.getText(), typeMC.getText(), subMC.getText(), superMC.getText(),
                pwrMC.getText(), tghMC.getText(), loyaltyMC.getText(), cmcMC.getText(), 100);

        //Convert the result into an observable list and show
        ObservableList<String> cards = FXCollections.observableArrayList();

        for (Map<String, Object> c: result) {
            String repr = "\nName: " + c.get("name") + "\nSet: " + c.get("set_id") + "\nLayout: " + c.get("layout") +
                    "\nType: " + c.get("type") + "\nColors: " + c.get("color") + "\nID: " + c.get("id");
            cards.add(repr);
        }

        listMC.setItems(cards);
        log("View owned cards request made", false, true);

    }

    @FXML private void removeCard() {

        // If a selection is made, remove card in selection from database
        SelectionModel m = listMC.getSelectionModel();
        Object o = m.getSelectedItem();
        if (o != null) {

            // Grab the ID field from the listed card, and remove from localDB
            String[] idfield = o.toString().split("\n");
            String id = idfield[6].substring(4);
            String l = idfield[3].substring(8);

            removeCardFromDB(id, l);
            log("Removed " + l + id, false, true);

        }

        // Update image to card back
        imageViewerWindow.reset();

        searchMyCards();

    }

    @FXML private void viewMyCard() {

        // If a selection is made, Show the image (if one is associated)
        SelectionModel m = listMC.getSelectionModel();
        Object o = m.getSelectedItem();

        if (o != null) {

            // Grab the ID field from the listed card, and remove from localDB
            String[] idfield = o.toString().split("\n");
            String id = idfield[6].substring(4);

            // Grab card from localDB and load its image from the internet
            Map<String, Object> card = DBCardByID(id);

            // If image is available, update
            if (card.get("image") != null) {

                // Set image
                imageViewerWindow.set(card.get("image").toString());

            }
            else { imageViewerWindow.reset(); }
        }

        // Log view card request
        log("View My Card Request made", false, false);

    }
    //------------------------------------------Deck Tab----------------------------------------------

    /**
     * Initializes tab information which needs initialized, such as defaulting ImageViews
     *
     */
    @FXML private void dTabInit() {



    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~END FXML PROCESSING CODE~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Closes all windows when main window is closed
     */
    public void closeWindows() {

        // Close other windows
        iView.close();
        log("Main window closed, closing other windows", false, true);

    }


    /**
     * Provides a set of instruction for when the program ends
     */
    public void onClose() {

        // Disconnect localDB and close app log
        log("Beginning onClose cleanup", false, true);
        logFile.close();
        localDB.disconnect();

    }
}