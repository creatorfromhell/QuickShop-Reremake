package org.maxgamer.quickshop.Shop;

import java.util.ArrayList;
import java.util.UUID;

import com.google.gson.Gson;
import lombok.*;
import org.jetbrains.annotations.*;

/**
 * Contains shop's moderators infomations, owner, staffs etc.
 */
@EqualsAndHashCode
@ToString
public class ShopModerator {
    private UUID owner;
    private ArrayList<UUID> staffs;

    private ShopModerator(@NotNull ShopModerator shopModerator) {
        this.owner = shopModerator.owner;
        this.staffs = shopModerator.staffs;
    }

    /**
     * Shop moderators, inlucding owner, and empty staffs.
     *
     * @param owner The owner
     */
    public ShopModerator(@NotNull UUID owner) {
        this.owner = owner;
        this.staffs = new ArrayList<UUID>();
    }

    /**
     * Shop moderators, inlucding owner, staffs.
     * @param owner The owner
     * @param staffs The staffs
     */
    public ShopModerator(@NotNull UUID owner, @NotNull ArrayList<UUID> staffs) {
        this.owner = owner;
        this.staffs = new ArrayList<UUID>();
    }

    public ShopModerator clone() {
        return new ShopModerator(this);
    }

    /**
     * Get moderators owner (Shop Owner).
     * @return Owner's UUID
     */
    public UUID getOwner() {
        return owner;
    }

    /**
     * Set moderators owner (Shop Owner)
     * @param player Owner's UUID
     */
    public void setOwner(@NotNull UUID player) {
        this.owner = player;
    }

    /**
     * Set moderators staffs
     * @param players staffs list
     */
    public void setStaffs(@NotNull ArrayList<UUID> players) {
        this.staffs = players;
    }

    /**
     * Add moderators staff to staff list
     * @param player New staff
     * @return Success
     */
    public boolean addStaff(@NotNull UUID player) {
        if (staffs.contains(player))
            return false;
        staffs.add(player);
        return true;
    }

    /**
     * Remove moderators staff from staff list
     * @param player Staff
     * @return Success
     */
    public boolean delStaff(@NotNull UUID player) {
        if (!staffs.contains(player))
            return false;
        staffs.remove(player);
        return true;
    }

    /**
     * Remove all staffs
     */
    public void clearStaffs() {
        staffs.clear();
    }

    /**
     * Get staffs list
     * @return Staffs
     */
    public ArrayList<UUID> getStaffs() {
        return staffs;
    }

    /**
     * Get a player is or not moderators owner
     * @param player Player
     * @return yes or no
     */
    public boolean isOwner(@NotNull UUID player) {
        return player.equals(owner);
    }

    /**
     * Get a player is or not moderators a staff
     * @param player Player
     * @return yes or no
     */
    public boolean isStaff(@NotNull UUID player) {
        return staffs.contains(player);
    }

    /**
     * Get a player is or not moderators
     * @param player Player
     * @return yes or no, return true when it is staff or owner
     */
    public boolean isModerator(@NotNull UUID player) {
        if (isOwner(player))
            return true;
        return isStaff(player);
    }

    public static String serialize(@NotNull ShopModerator shopModerator) {
        Gson gson = new Gson();
        return gson.toJson(shopModerator); //Use Gson serialize this class
    }

    public static ShopModerator deserialize(@NotNull String serilized) {
        //Use Gson deserialize data
        Gson gson = new Gson();
        return gson.fromJson(serilized, ShopModerator.class);
    }
}
