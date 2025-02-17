package org.maxgamer.quickshop.Economy;

import net.tnemc.core.Reserve;
import net.tnemc.core.economy.EconomyAPI;
import org.bukkit.Bukkit;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @author creatorfromhell
 */
public class Economy_Reserve implements EconomyCore {

  private EconomyAPI reserve = null;

  public Economy_Reserve() {
    setup();
  }

  private void setup() {
    if(((Reserve)Bukkit.getPluginManager().getPlugin("Reserve")).economyProvided()) {
      reserve = ((Reserve)Bukkit.getPluginManager().getPlugin("Reserve")).economy();
    }
  }

  /**
   * Checks that this economy is valid. Returns false if it is not valid.
   *
   * @return True if this economy will work, false if it will not.
   */
  @Override
  public boolean isValid() {
    return reserve != null;
  }

  /**
   * Formats the given number... E.g. 50.5 becomes $50.5 Dollars, or 50 Dollars 5 Cents
   *
   * @param balance The given number
   *
   * @return The balance in human readable text.
   */
  @Override
  public String format(double balance) {
    return reserve.format(new BigDecimal(balance));
  }

  /**
   * Deposits a given amount of money from thin air to the given username.
   *
   * @param name The exact (case insensitive) username to give money to
   * @param amount The amount to give them
   *
   * @return True if success (Should be almost always)
   */
  @Override
  public boolean deposit(UUID name, double amount) {
    return reserve.addHoldings(name, new BigDecimal(amount));
  }

  /**
   * Withdraws a given amount of money from the given username and turns it to thin air.
   *
   * @param name The exact (case insensitive) username to take money from
   * @param amount The amount to take from them
   *
   * @return True if success, false if they didn't have enough cash
   */
  @Override
  public boolean withdraw(UUID name, double amount) {
    return reserve.removeHoldings(name, new BigDecimal(amount));
  }

  /**
   * Transfers the given amount of money from Player1 to Player2
   *
   * @param from The player who is paying money
   * @param to The player who is receiving money
   * @param amount The amount to transfer
   *
   * @return true if success (Payer had enough cash, receiver was able to receive the funds)
   */
  @Override
  public boolean transfer(UUID from, UUID to, double amount) {
    return reserve.transferHoldings(from, to, new BigDecimal(amount));
  }

  /**
   * Fetches the balance of the given account name
   *
   * @param name The name of the account
   *
   * @return Their current balance.
   */
  @Override
  public double getBalance(UUID name) {
    return reserve.getHoldings(name).doubleValue();
  }
}
