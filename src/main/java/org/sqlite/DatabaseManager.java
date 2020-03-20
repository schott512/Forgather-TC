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

package org.sqlite;
import java.sql.*;
import java.io.*;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * Class designed to handle all database interactions. Users should not have to write any sql code
 * outside of this class, only make the appropriate calls to it.
 */
public class DatabaseManager {
    private static Connection db = null;
    private static PrintWriter logFile = null;

    /**
     * Constructor which initializes our connection, statement, and other class variables
     */
    public DatabaseManager() {

        // Grab current data/time and format it into a string
        java.util.Date date = Calendar.getInstance().getTime();
        Format formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String logDate = formatter.format(date);

        // Open log file
        try {
            String s = File.separator;
            String path = System.getProperty("user.dir")+ s + "logs" + s + "db-log.txt";
            FileWriter fw = new FileWriter(path, true);
            BufferedWriter bw = new BufferedWriter(fw);
            logFile = new PrintWriter(bw);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Start the log entry with date and time
        log( "========================================" + logDate + "========================================",
                true, true);

        // Create database connection
        connect();

        // Ensure the db we are working with has been initialized
        initializeDB();

    }

    /**
     * Connect to ForgatherDB.sqlite, set db to this connection.
     */
    private static void connect() {
        try {
            // Path we expect to find the DB at
            String s = File.separator;
            String path = "jdbc:sqlite:" + System.getProperty("user.dir") + s + "db" + s +"Forgather.sqlite";
            // create a connection to the database
            db = DriverManager.getConnection(path);

            log("Connection to SQLite has been established.", false, false);
        }
        catch (SQLException e) {
            log(e.getMessage(), false, true);
        }
    }

    /**
     * Runs Sqlite statements which creates all tables within DB.
     * Statements have no effect if database is already built.
     * Assumes DB connection db exists.
     */
    private void initializeDB() {

        // A list of sql queries to execute, creates all tables if not present
        String[] create_cmds =
                {"CREATE TABLE IF NOT EXISTS Cards (\n" +
                        " layout text NOT NULL, \n" +
                        " name text NOT NULL, \n" +
                        " id text PRIMARY KEY, \n" +
                        " color text NOT NULL, \n" +
                        " image blob, \n" +
                        " card_text text NOT NULL, \n" +
                        " type text NOT NULL, \n" +
                        " subtype text NOT NULL, \n" +
                        " supertype text NOT NULL, \n" +
                        " mana_cost text, \n" +
                        " cmc INTEGER , \n" +
                        " power INTEGER, \n" +
                        " toughness INTEGER, \n" +
                        " loyalty INTEGER, \n" +
                        " set_id text NOT NULL, \n" +
                        " FOREIGN KEY(set_id) REFERENCES Sets(id) \n" +
                        ");",
                        "CREATE TABLE IF NOT EXISTS Decks (\n" +
                                " id INTEGER PRIMARY KEY AUTOINCREMENT, \n" +
                                " color text NOT NULL, \n" +
                                " card_count INTEGER NOT NULL, \n" +
                                " name text NOT NULL, \n" +
                                " box_id text NOT NULL, \n" +
                                " FOREIGN KEY(box_id) REFERENCES Boxes(id) \n" +
                                ");",
                        "CREATE TABLE IF NOT EXISTS Sets (\n" +
                                " id text PRIMARY KEY, \n" +
                                " name text NOT NULL, \n" +
                                " release DATE NOT NULL, \n" +
                                " block text NOT NULL \n" +
                                ");",
                        "CREATE TABLE IF NOT EXISTS Boxes (\n" +
                                " id INTEGER PRIMARY KEY, \n" +
                                " name text, \n" +
                                " location text NOT NULL \n" +
                                ");",
                        "CREATE TABLE IF NOT EXISTS Double_faced (\n" +
                                " card1 text NOT NULL, \n" +
                                " card2 text NOT NULL, \n" +
                                " image blob, " +
                                " PRIMARY KEY(card1, card2), \n" +
                                " FOREIGN KEY(card1, card2) REFERENCES Cards(id, id) \n" +
                                ");",
                        "CREATE TABLE IF NOT EXISTS FlipSplit_cards (\n" +
                                " card1 text NOT NULL, \n" +
                                " card2 text NOT NULL, \n" +
                                " image blob NOT NULL, \n" +
                                " PRIMARY KEY(card1, card2), \n" +
                                " FOREIGN KEY(card1, card2) REFERENCES Cards(id, id) \n" +
                                ");",
                        "CREATE TABLE IF NOT EXISTS Card_to_Deck (\n" +
                                " card_id text NOT NULL, \n" +
                                " deck_id text NOT NULL, \n" +
                                " copies INTEGER NOT NULL, \n" +
                                " PRIMARY KEY(card_id, deck_id), \n" +
                                " FOREIGN KEY(card_id) REFERENCES Cards(id), \n" +
                                " FOREIGN KEY(deck_id) REFERENCES Decks(id) \n" +
                                ");",
                        "CREATE TABLE IF NOT EXISTS Meld (\n" +
                                "card1 text NOT NULL, \n" +
                                "card2 text NOT NULL, \n" +
                                "card3 text PRIMARY KEY, \n" +
                                "image blob \n" +
                                ");",
                        "CREATE TABLE IF NOT EXISTS META_DATA (\n" +
                                "version text PRIMARY KEY, \n" +
                                "local_images boolean NOT NULL, \n" +
                                "log INTEGER NOT NULL, \n" +
                                "background text NOT NULL \n" +
                                ");"};

        // Run each command
        for (String sql : create_cmds) {
            runSql(sql);
        }

        // Insert default data
        Object[] data = {-1, "Collection", "ALL", 0, -1};
        addTo("DECKS", "(id, name, color, card_count, box_id)", data, true);

        Object[] box = {-1, "Undecided", "Nowhere"};
        addTo("BOXES", "(id, name, location)", box, true);

        log("Initialized",true, true);

    }

    /**
     * Closes current database connection.
     */
    public void disconnect() {
        try {
            if (db != null) {
                db.close();
                log("=========================================DB closed=================================================", false, false);
                logFile.close();
            }
        }
        catch (SQLException e) {
            log(e.getMessage(), false, true);
            logFile.close();
        }
    }

    /**
     * Adds an entry to the sqlite database.
     * @param table A string specifying table to insert into
     * @param parameters A string formatted as (id, name, etc) for any number of values to be inserted into table
     * @param data An array of Objects. The data to be inserted
     * @param ignore A boolean value indicating whether or not to add the "OR IGNORE" clause to the insert
     */
    public void addTo(String table, String parameters, Object[] data, Boolean ignore) {

        // Build up a string which is our statement to run
        StringBuilder sql = new StringBuilder("INSERT ");
        if (ignore) {sql.append(" OR IGNORE ");}
        sql.append("INTO ");
        sql.append(table);
        sql.append(parameters);
        sql.append(" VALUES (");

        // Process array of objects and build up statement
        for (int i = 0; i < data.length; ++i) {
            // If data is a string, surround with '
            if (data[i] instanceof String) {
                sql.append("'");
                sql.append(data[i]);
                sql.append("'");
            }
            else {
                sql.append(data[i]);
            }
            // If not last object, add ", "
            if (i != data.length-1) {
                sql.append(", ");
            }
        }

        //finish and execute sql statement
        sql.append(");");
        runSql(sql.toString());

    }

    /**
     * Deletes rows from a table which meet a search condition. Designed so that you don't actually have to write
     * out full sql statements in other objects, as this manager is supposed to handle all of that!
     * @param table A string indicating a table to remove from
     * @param condition A string formatted as "value == data", value is the name of a database key in table.
     *                  data can be a string surrounded by '', or any other datatype accepted by sqlite.
     *                  In place of ==, <> (not equal), or any other comparator may be used.
     *                  "'data' LIKE %String% is valid for any string data.
     *                  Additional conditions can be added by use of the AND keyword.
     *                  In order to empty a table entirely, simply provide an empty string; ""
     */
    public void removeFrom(String table, String condition) {

        // create delete string to be run
        String sql = "DELETE FROM " + table;

        // If condition is non-empty, add where clause. Execute sql
        if (!condition.equals("")){ sql += " WHERE " + condition;}
        sql += ";";
        runSql(sql);

    }

    /**
     * Updates column(s) of a particular row in table. Does not support LIMIT or ORDER BY. (yet)
     * @param table A string indicating a table to remove from
     * @param set A string indicating the updates to make. Formatted "value = data". Use of comma is
     *                acceptable to allow multiple values, ie "value1 = data1, value2 = data2".
     * @param condition A string formatted as "value == data", value is the name of a database key in table.
     *                  data can be a string surrounded by '', or any other datatype accepted by sqlite.
     *                  In place of ==, <> (not equal), or any other comparator may be used.
     *                  "'data' LIKE %String% is valid for any string data.
     *                  Additional conditions can be added by use of the AND keyword.
     *                  In order to change table entirely, simply provide an empty string; ""
     */
    public void update(String table, String set, String condition) {

        // Create update string
        String sql = "UPDATE " + table + " SET " + set;
        if (!condition.equals("")){ sql += " WHERE " + condition;}
        sql += ";";
        runSql(sql);

    }

    /**
     * Adds an entry to the sqlite database if it does not exist, replaces it if it does.
     * @param table A string specifying table to insert into
     * @param parameters A string formatted as (id, name, etc) for any number of values to be inserted into table
     * @param data An array of Objects. The data to be inserted
     * @param identifier A string specifying what to use as a where clause. Should uniquely identify the object
     *                   you wish to upsert, it's a good idea to use the primary key and it's value.
     */
    public void upsert(String table, String parameters, Object[] data, String identifier) {

        // First, run a delete. If nothing matches nothing will be deleted
        removeFrom(table, identifier);

        // Then insert the data
        addTo(table, parameters, data, false);

    }

    /**
     *  Creates and executes a sql select statement
     *  @param sel A string indicating what data to select; Defaults to *. Formatted "column1, column2, etc"
     *  @param fr A string indicating table or tables to pull from; Formatted as "TABLE t1, TABLE t2" where
     *              multiple values are optional, t1 and t2 are examples of naming the tables
     *  @param wh A string indicating conditions. Formatted as "key == value", supports "and" clause. use "" for none
     *  @param ob A string indicating an ORDER BY clause, use "" for none
     *  @param lim An int indicating the maximum number of rows to return
     *  @param gb A string indicating a GROUP BY clause, use "" for none
     *  @param hv A string indicating a HAVING clause, use "" for none
     */
    public List<Map<String, Object>> searchDB(String sel, String fr, String wh, String ob, int lim, String gb, String hv) {

        // Build beginning of SELECT FROM statement
        String sql = "SELECT " + sel + " FROM " + fr;

        // Add each query optionally
        if (!wh.equals("")) { sql += " WHERE " + wh;}
        if (!ob.equals("")) { sql += " ORDER BY " + ob;}
        if (lim != 0) {sql += " LIMIT " + lim;}
        if (!gb.equals("")) { sql += " GROUP BY " + gb;}
        if (!hv.equals("")) { sql += " HAVING " + hv;}

        sql += ";";

        // Run and Fetch query results
        return runQuery(sql);
    }


    /**
     * Runs a sql command enclosing it in a try/catch statement. Eliminates repeated use of try/catch.
     * Handles sql errors simply by printing them. Do not use if errors must be explicitly handled
     * in another manner. Also logs all sql queries given for debug purposes.
     * @param sql A string which is the sql statement to be executed
     */
    private static void runSql(String sql) {

        // First, log the command we are attempting to run
        log(sql, true, false);

        // Attempt execution of command, log error if failure
        try {
            Statement stmt = db.createStatement();
            stmt.execute(sql);
            stmt.close();
        }
        catch (SQLException e) {
            log(e.getMessage(), false, true);
        }
    }

    /**
     * Runs a sql query enclosing it in a try/catch statement. Returns list of maps containing returned data, row by row
     * Eliminates repeated use of try/catch.
     * Handles sql errors simply by printing them. Do not use if errors must be explicitly handled
     * in another manner. Also logs all sql queries given for debug purposes.
     * @param sql A string which is the sql statement to be executed
     */
    private static List<Map<String, Object>> runQuery(String sql) {

        // Log the query
        log(sql, true, false);

        // List of rows, represented by maps, to represent table
        List<Map<String, Object>> resultList = new ArrayList<>();

        // Attempt execution of command, log error if failure
        try {
            Statement stmt = db.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            // Map to represent row. key is column name String, value is the value of that column in row
            Map<String, Object> row;

            // Grab necessary metaData for getting column name and how many columns per row
            ResultSetMetaData metaData = rs.getMetaData();
            Integer columnCount = metaData.getColumnCount();

            // Build up row until all columns inserted
            while (rs.next()) {
                row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnName(i), rs.getObject(i));
                }
                // Add row to list of rows
                resultList.add(row);
            }
            // Close resultSet and Statement
            stmt.close();
            rs.close();

        } catch (SQLException e) {
            log(e.getMessage(), false, true);
        }

        return resultList;
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

}
