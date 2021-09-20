package me.imdanix.liquid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

public class Database {
    private Connection connection;

    public Database(Plugin plugin) {
        plugin.getDataFolder().mkdirs();
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder() + File.separator + "blocks.db");
            Statement statement = connection.createStatement();
            statement.execute("CREATE TABLE IF NOT EXISTS `blocks` (" +
                    "`x` INTEGER NOT NULL," +
                    "`y` INTEGER NOT NULL," +
                    "`z` INTEGER NOT NULL," +
                    "`world` VARCHAR(64) NOT NULL" +
                    ");");
            statement.close();
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public Set<Location> loadLocations() {
        Set<Location> locations = new LinkedHashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM `blocks`;")) {
            ResultSet result = statement.executeQuery();
            while (result.next()) {
                locations.add(new Location(Bukkit.getWorld(result.getString("world")), result.getInt("x"), result.getInt("y"), result.getInt("z")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return locations;
    }

    public void removeLocation(Location loc) {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM `blocks` WHERE `x` = ? AND `y` = ? AND `z` = ? AND `world` = ?;")) {
            statement.setInt(1, loc.getBlockX());
            statement.setInt(2, loc.getBlockY());
            statement.setInt(3, loc.getBlockZ());
            statement.setString(4, loc.getWorld().getName());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveLocation(Location loc) {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO `blocks`(x, y, z, world) VALUES (?, ?, ?, ?);")) {
            statement.setInt(1, loc.getBlockX());
            statement.setInt(2, loc.getBlockY());
            statement.setInt(3, loc.getBlockZ());
            statement.setString(4, loc.getWorld().getName());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}