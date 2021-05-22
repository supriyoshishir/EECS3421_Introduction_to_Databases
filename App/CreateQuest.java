import java.util.*;
import java.net.*;
import java.text.*;
import java.lang.*;
import java.io.*;
import java.sql.*;
import pgpass.*;
import java.util.Date;

public class CreateQuest {

    private Date day;
    private String realm;
    private String theme;
    private Integer amount;
    private float seed;
    private String user;
    private String url;
    private Connection con;

    public CreateQuest (String[] args) {

        // Command line input setup 
        if (args.length != 6) {
            System.out.println("\nUsage: java CreateQuest 'day' 'realm' 'theme' amount user seed\n");
            System.exit(0);
        } else {
            try {
                day = new SimpleDateFormat("yyyy-MM-dd").parse(args[0]);
                realm = new String(args[1]);
                theme = new String(args[2]);
                amount = Integer.parseInt(args[3]);
                user = new String(args[4]);
                seed = Float.parseFloat(args[5]);
            } catch (NumberFormatException | ParseException e) {
                System.out.println("\nUsage: java CreateQuest 'day' 'realm' 'theme' amount user seed\n");
                System.exit(0);
            }
        }

        // Set up the DB connection.
        try {
            // Register the driver with DriverManager.
            Class.forName("org.postgresql.Driver").newInstance();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            System.exit(0);
        } catch (InstantiationException e) {
            e.printStackTrace();
            System.exit(0);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            System.exit(0);
        }

        // set up account info and fetch the password from <.pgpass>
        Properties props = new Properties();
        try {
            String passwd = PgPass.get("db", "*", user, user);
            props.setProperty("user", user);
            props.setProperty("password", passwd);
        } catch(PgPassException e) {
            System.out.println("\nCould not obtain PASSWORD from <.pgpass>\n");
            System.out.println(e.toString());
            System.exit(0);
        }

        url = "jdbc:postgresql://db:5432/";

        // Initialize the connection
        try {
            // Connect with a fall-thru id & password
            con = DriverManager.getConnection(url, props);
        } catch(SQLException e) {
            System.out.println("\nSQL database connection error\n");
            System.out.println(e.toString());
            System.exit(0);
        }    

        try {
            con.setAutoCommit(false);
        } catch(SQLException e) {
            System.out.println("\nFailed to turn autocommit off\n");
            e.printStackTrace();
            System.exit(0);
        }    

        // Is the day in the future?
        if (!dayCheck()) {
            System.out.println("\nday is not in future\n");
            System.exit(0);
        }
        //else {
        //    System.out.println("\nday is in future\n");
        //}

        // Does the realm exist in the Realm Table?
        if (!realmCheck()) {
            System.out.println("\nrealm does not exist\n");
            System.exit(0);
        }
        //else {
        //    System.out.println("\nrealm exists\n");
        //}

        // Does the amount exceeds the floor of the SUM?
        if (!amountCheck()) {
            System.out.println("\namount exceeds what is possible\n");
            System.exit(0);
        }
        //else {
        //    System.out.println("\namount in range\n");
        //}

        // Is the seed value between -1.0 and 1.0, inclusive?
        if (!seedCheck()) {
            System.out.println("\nseed value is improper\n");
            System.exit(0);
        }
        //else {
        //    System.out.println("\nseed value is proper\n");
        //}

        // Insert new Quest in the Quest Table
        insertQuest();
        //System.out.println("\nNew Quest inserted successfully\n");

        // Insert new Loot in the Loot Table
        insertLoot();
        //System.out.println("\nNew Loot inserted successfully\n");

        // Commit and close the connection.
        try {
            con.commit();
            con.close();
        } catch(SQLException e) {
            System.out.println("\nFailed to commit & close\n");
            e.printStackTrace();
            System.exit(0);
        }    
    }

    public boolean dayCheck() {

        boolean inFuture = true;
        Date current_day = new Date(System.currentTimeMillis());

        if (day.compareTo(current_day) > 0) {
            inFuture = true;
        } else {
            inFuture = false;
        }
        return inFuture;
    }

    public boolean realmCheck() {

        PreparedStatement querySt = null;
        ResultSet answers = null;
        boolean inTable  = true;

        String queryText = "SELECT * FROM Realm";

        try {
            querySt = con.prepareStatement(queryText);
            answers = querySt.executeQuery();
            List<String> table = new ArrayList<String>();
            while(answers.next()) {
                table.add(answers.getString(1));
            }
            if (table.contains(realm)) {
                inTable = true;
            }else {
                inTable = false;
            }
            answers.close();
            querySt.close();
        } catch(SQLException e) {
            System.out.println("\nSQL failed in realmCheck\n");
            System.out.println(e.toString());
            System.exit(0);
        }
        return inTable;
    }

    public boolean amountCheck() {

        PreparedStatement querySt = null;
        ResultSet answers = null;
        boolean inRange = true;

        String queryText = "SELECT SUM(sql) AS sum FROM Treasure";
    
        try {
            querySt = con.prepareStatement(queryText);
            answers = querySt.executeQuery();
            while(answers.next()) {
            int result = answers.getInt(1);
                if (amount < result) {
                    inRange = true;
                } else {
                    inRange = false;
                }
            }
            answers.close();
            querySt.close();
        } catch(SQLException e) {
            System.out.println("\nSQL failed in amountCheck\n");
            System.out.println(e.toString());
            System.exit(0);
        }
        return inRange;
    }

    public boolean seedCheck() {

        boolean inRange = true;

        if (-1.0 <= seed && seed <= 1.0) {
            inRange = true;
        } else {
            inRange = false;
        }
        return inRange;
    }

    public void insertQuest() {

        PreparedStatement querySt = null;
        int answers = 0;
        java.sql.Date sqlday = new java.sql.Date(day.getTime());

        String queryText = "INSERT INTO Quest (theme, realm, day, succeeded) VALUES (?, ?, ?, ?)";

        try {
            querySt = con.prepareStatement(queryText);
            querySt.setString(1, theme.toString());
            querySt.setString(2, realm);
            querySt.setDate(3, sqlday);
            querySt.setNull(4, Types.NULL);
            answers = querySt.executeUpdate();
            querySt.close();
        } catch(SQLException e) {
        System.out.println("\nSQL failed in insertQuest\n");
        System.out.println(e.toString());
        System.exit(0);
        }
    }
 
    public void insertLoot() {

        PreparedStatement setseedPS, queryPS, insertPS = null;
        ResultSet setseedEQ, queryRS = null;
        int insertEU, totalsql = 0;
        java.sql.Date sqlday = new java.sql.Date(day.getTime());

        String seedText = "SELECT setseed(?)";

        String queryText = "WITH RandomTable (treasure, sql) AS "
                + "(SELECT treasure, sql "
                + "FROM Treasure ORDER BY RANDOM()) "
                + "SELECT ROW_NUMBER() OVER () AS loot_id, treasure, sql "
                + "FROM RandomTable";
        
        String insertText = "INSERT INTO Loot (loot_id, treasure, theme, realm, day, login) VALUES (?, ?, ?, ?, ?, ?)";

        try {
            setseedPS = con.prepareStatement(seedText);
            setseedPS.setFloat(1, seed);
            setseedEQ = setseedPS.executeQuery();
            queryPS = con.prepareStatement(queryText);
            queryRS = queryPS.executeQuery();
            insertPS = con.prepareStatement(insertText);
            while(queryRS.next()) {
                totalsql += queryRS.getInt(3);
                if (totalsql < amount) {
                    insertPS.setInt(1, queryRS.getInt(1));
                    insertPS.setString(2, queryRS.getString(2));
                    insertPS.setString(3, theme);
                    insertPS.setString(4, realm);
                    insertPS.setDate(5, sqlday);
                    insertPS.setNull(6, Types.NULL);
                    insertEU = insertPS.executeUpdate();
                }
            }
            insertPS.close();
            queryRS.close();
            queryPS.close();
            setseedEQ.close();
            setseedPS.close();
        } catch(SQLException e) {
        System.out.println("\nSQL failed in insertLoot\n");
        System.out.println(e.toString());
        System.exit(0);
        }
    }
    public static void main(String[] args) {
        CreateQuest cq = new CreateQuest(args);
    }
}