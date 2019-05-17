package net.porillo.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import net.porillo.GlobalWarming;
import net.porillo.config.Lang;
import net.porillo.database.tables.PlayerTable;
import net.porillo.engine.ClimateEngine;
import net.porillo.engine.api.WorldClimateEngine;
import net.porillo.engine.models.CarbonIndexModel;
import net.porillo.objects.GPlayer;
import net.porillo.objects.OffsetBounty;
import net.porillo.util.AlertManager;
import net.porillo.util.ChatTable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;

import java.text.DecimalFormat;
import java.util.*;

import static org.bukkit.ChatColor.*;

@CommandAlias("globalwarming|gw")
public class GeneralCommands extends BaseCommand {
    private static final long SPAM_INTERVAL_TICKS = GlobalWarming.getInstance().getConf().getSpamInterval();
    private static final UUID untrackedUUID = UUID.fromString("1-1-1-1-1");
    private List<UUID> playerRequestList;

    public GeneralCommands() {
        playerRequestList = new ArrayList<>();
        debounceRequests();
    }

    @HelpCommand
    public void onHelp(GPlayer gPlayer, CommandHelp help) {
        if (isCommandAllowed(gPlayer)) {
            help.showHelp();
        }
    }

    @Subcommand("bounty")
    @CommandPermission("globalwarming.bounty")
    public class BountyCommand extends BaseCommand {

        @Subcommand("")
        @Description("Display all active bounties")
        @Syntax("")
        @CommandPermission("globalwarming.bounty")
        public void onBounty(GPlayer gPlayer) {
            if (isCommandAllowed(gPlayer)) {
                OffsetBounty.show(gPlayer);
            }
        }

        @Subcommand("create")
        @Description("Create a tree-planting bounty to reduce your carbon footprint")
        @Syntax("[tree-blocks] [reward]")
        @CommandPermission("globalwarming.bounty.create")
        public void onBountyCreate(GPlayer gPlayer, String[] args) {
            if (isCommandAllowed(gPlayer)) {
                int treeBlocks = 0;
                int reward = 0;
                if (args.length == 2) {
                    treeBlocks = Integer.parseInt(args[0]);
                    reward = Integer.parseInt(args[1]);
                }

                if (treeBlocks > 0 && reward > 0) {
                    OffsetBounty.create(gPlayer, treeBlocks, reward);
                } else {
                    gPlayer.sendMsg(String.format(
                          Lang.GENERIC_INVALIDARGS.get(),
                          "[tree-blocks:integer] [reward:integer]"));
                }
            }
        }

        @Subcommand("join")
        @Description("Join a tree-planting bounty for a reward (see: /gw bounty list)")
        @Syntax("[bounty_id]")
        @CommandPermission("globalwarming.bounty.join")
        public void onBountyJoin(GPlayer gPlayer, String[] args) {
            if (isCommandAllowed(gPlayer)) {
                int bountyId = 0;
                if (args.length == 1) {
                    bountyId = Integer.parseInt(args[0]);
                }

                if (bountyId > 0) {
                    OffsetBounty bounty = OffsetBounty.join(gPlayer, bountyId);
                    Player onlinePlayer = gPlayer.getOnlinePlayer();
                    if (onlinePlayer != null && bounty != null) {
                        OffsetBounty.notify(
                              bounty,
                              String.format(Lang.BOUNTY_ACCEPTEDBY.get(), onlinePlayer.getName()),
                              Lang.BOUNTY_ACCEPTED.get()
                        );
                    }
                } else {
                    gPlayer.sendMsg(String.format(
                          Lang.GENERIC_INVALIDARGS.get(),
                          "[bounty_id:integer]"));
                }
            }
        }

        @Subcommand("unjoin")
        @Description("Abandon a bounty you joined")
        @Syntax("")
        @CommandPermission("globalwarming.bounty.cancel")
        public void onBountyUnjoin(GPlayer gPlayer) {
            if (isCommandAllowed(gPlayer)) {
                OffsetBounty bounty = OffsetBounty.unJoin(gPlayer);
                if (bounty == null) {
                    gPlayer.sendMsg(Lang.BOUNTY_NOTJOINED);
                } else {
                    Player onlinePlayer = gPlayer.getOnlinePlayer();
                    if (onlinePlayer != null) {
                        OffsetBounty.notify(
                              bounty,
                              gPlayer,
                              String.format(Lang.BOUNTY_ABANDONEDBY.get(), onlinePlayer.getName()),
                              Lang.BOUNTY_ABANDONED.get()
                        );
                    }
                }
            }
        }

        @Subcommand("cancel")
        @Description("Cancel any idle bounties you created")
        @Syntax("")
        @CommandPermission("globalwarming.bounty.cancel")
        public void onBountyCancel(GPlayer gPlayer) {
            if (isCommandAllowed(gPlayer)) {
                OffsetBounty.cancel(gPlayer);
            }
        }
    }

    @Subcommand("score")
    @CommandPermission("globalwarming.score")
    public class ScoreCommand extends BaseCommand {

        @Subcommand("")
        @Description("Get your carbon score")
        @Syntax("")
        @CommandPermission("globalwarming.score")
        public void onScore(GPlayer gPlayer) {
            if (isCommandAllowed(gPlayer)) {
                showCarbonScore(gPlayer);
            }
        }

        @Subcommand("show")
        @Description("Show the scoreboard")
        @Syntax("")
        @CommandPermission("globalwarming.score.show")
        public void onShow(GPlayer gPlayer) {
            if (isCommandAllowed(gPlayer)) {
                GlobalWarming.getInstance().getScoreboard().show(gPlayer, true);
            }
        }

        @Subcommand("alerts")
        @Description("Sends message alerts on all carbon activities")
        @Syntax("")
        @CommandPermission("globalwarming.score.alerts")
        public void onAlert(GPlayer gPlayer) {
            if (isCommandAllowed(gPlayer)) {
                if (!AlertManager.getInstance().isSubscribed(gPlayer.getUuid())) {
                    AlertManager.getInstance().subscribe(gPlayer.getUuid());
                    gPlayer.sendMsg(Lang.ALERT_SUBSCRIBE.get());
                } else {
                    AlertManager.getInstance().unsubscribe(gPlayer.getUuid());
                    gPlayer.sendMsg(Lang.ALERT_UNSUBSCRIBE.get());
                }
            }
        }

        @Subcommand("hide")
        @Description("Hide the scoreboard")
        @Syntax("")
        @CommandPermission("globalwarming.score.hide")
        public void onHide(GPlayer gPlayer) {
            if (isCommandAllowed(gPlayer)) {
                GlobalWarming.getInstance().getScoreboard().show(gPlayer, false);
            }
        }
    }

    @Subcommand("top")
    @CommandPermission("globalwarming.top")
    public class TopCommand extends BaseCommand {

        @Subcommand("")
        @Description("Display the top ten polluters and planters")
        @CommandPermission("globalwarming.top")
        public void onTop(GPlayer gPlayer) {
            if (isCommandAllowed(gPlayer)) {
                showTopTen(gPlayer, true);
                showTopTen(gPlayer, false);
            }
        }

        @Subcommand("polluter")
        @Description("Display the top ten polluters")
        @CommandPermission("globalwarming.top.polluter")
        public void onTopPolluter(GPlayer gPlayer) {
            if (isCommandAllowed(gPlayer)) {
                showTopTen(gPlayer, true);
            }
        }

        @Subcommand("planter")
        @Description("Display the top ten tree-planters")
        @CommandPermission("globalwarming.top.planter")
        public void onTopPlanter(GPlayer gPlayer) {
            if (isCommandAllowed(gPlayer)) {
                showTopTen(gPlayer, false);
            }
        }
    }

    @Subcommand("booklet")
    @Description("Add the instructional booklet to your inventory")
    @CommandPermission("globalwarming.booklet")
    public void onBooklet(GPlayer gPlayer) {
        if (isCommandAllowed(gPlayer)) {
            getBooklet(gPlayer);
        }
    }

    /**
     * True when:
     * - The player is not spamming
     * - The player's climate-engine is enabled
     */
    private boolean isCommandAllowed(GPlayer gPlayer) {
        boolean isCommandAllowed = false;
        if (isSpamming(gPlayer)) {
            gPlayer.sendMsg(Lang.GENERIC_SPAM);
        } else if (!ClimateEngine.getInstance().isClimateEngineEnabled(gPlayer.getAssociatedWorldId())) {
            gPlayer.sendMsg(Lang.ENGINE_DISABLED);
        } else {
            isCommandAllowed = true;
        }

        return isCommandAllowed;
    }

    /**
     * Limit player requests per interval
     * - Valid requests store that player in a list
     * - The player-request list is cleared periodically
     */
    private boolean isSpamming(GPlayer gPlayer) {
        boolean isSpamming = true;
        if (gPlayer != null) {
            synchronized (this) {
                if (!playerRequestList.contains(gPlayer.getUuid())) {
                    playerRequestList.add(gPlayer.getUuid());
                    isSpamming = false;
                }
            }
        }

        return isSpamming;
    }

    /**
     * Clear the spam list periodically
     */
    private void debounceRequests() {
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(
              GlobalWarming.getInstance(),
              () -> {
                  synchronized (this) {
                      playerRequestList.clear();
                  }
              }, 0L, SPAM_INTERVAL_TICKS);
    }

    /**
     * Format a carbon index
     * - Map the value to color heat
     * - Maximum of two decimal places
     */
    private static String formatIndex(double index, int score) {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        return String.format("%s%s",
              getScoreColor(score),
              decimalFormat.format(index));
    }

    /**
     * Format a carbon score
     * - Map the value to color heat
     */
    private static String formatScore(int score) {
        return String.format("%s%d",
              getScoreColor(score),
              score);
    }

    /**
     * Format a temperature
     * - Map the value to color heat
     * - Maximum of two decimal places
     */
    private static String formatTemperature(double temperature) {
        ChatColor color = getTemperatureColor(temperature);
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        return String.format("%s%s",
              color,
              decimalFormat.format(temperature));
    }

    /**
     * Get the color associated with a carbon score
     * - Values are mapped to color-heat from LOW CO2 (cold) to HIGH CO2 (hot)
     * - These ranges are somewhat arbitrary
     */
    public static ChatColor getScoreColor(int score) {
        ChatColor color;
        if (score <= -3500) {
            color = DARK_BLUE;
        } else if (score <= -2500) {
            color = BLUE;
        } else if (score <= -1500) {
            color = DARK_AQUA;
        } else if (score <= -500) {
            color = AQUA;
        } else if (score <= 500) {
            color = GREEN; // (-500, 500]
        } else if (score <= 1500) {
            color = YELLOW;
        } else if (score <= 2500) {
            color = GOLD;
        } else if (score <= 3500) {
            color = RED;
        } else {
            color = DARK_RED;
        }

        return color;
    }

    /**
     * Get the color associated with a temperature
     * - These ranges are somewhat arbitrary
     */
    public static ChatColor getTemperatureColor(double temperature) {
        ChatColor color;
        if (temperature <= 10.5) {
            color = DARK_BLUE;
        } else if (temperature <= 11.5) {
            color = BLUE;
        } else if (temperature <= 12.5) {
            color = DARK_AQUA;
        } else if (temperature <= 13.5) {
            color = AQUA;
        } else if (temperature <= 14.5) {
            color = GREEN; // (13.5, 14.5]
        } else if (temperature <= 15.5) {
            color = YELLOW;
        } else if (temperature <= 16.5) {
            color = GOLD;
        } else if (temperature <= 17.5) {
            color = LIGHT_PURPLE;
        } else if (temperature <= 18.5) {
            color = RED;
        } else {
            color = DARK_RED;
        }

        return color;
    }

    /**
     * Show the player's carbon score as a chat message
     */
    public static void showCarbonScore(GPlayer gPlayer) {
        Player onlinePlayer = gPlayer.getOnlinePlayer();
        if (onlinePlayer != null) {
            //Do not show scored for worlds with disabled climate-engines:
            // - Note: temperature is based on the player's associated-world (not the current world)
            WorldClimateEngine associatedClimateEngine =
                  ClimateEngine.getInstance().getClimateEngine(gPlayer.getAssociatedWorldId());

            if (associatedClimateEngine != null && associatedClimateEngine.isEnabled()) {
                int score = gPlayer.getCarbonScore();
                double temperature = associatedClimateEngine.getTemperature();
                StringBuilder welcomeMessage = new StringBuilder();
                //Player's carbon score and the global temperature:
                welcomeMessage.append(String.format(
                      Lang.SCORE_CHAT.get(),
                      formatScore(score),
                      formatTemperature(temperature)));

                //What the target is (i.e., a point of reference):
                welcomeMessage.append("\n");
                welcomeMessage.append(Lang.TEMPERATURE_AVERAGE.get());

                //Guidance based on the global temperature:
                if (temperature < (14.0 - GlobalWarming.getInstance().getConf().getDegreesUntilChangeDetected())) {
                    welcomeMessage.append("\n");
                    welcomeMessage.append(Lang.TEMPERATURE_LOW.get());
                } else if (temperature < (14.0 + GlobalWarming.getInstance().getConf().getDegreesUntilChangeDetected())) {
                    welcomeMessage.append("\n");
                    welcomeMessage.append(Lang.TEMPERATURE_BALANCED.get());
                } else {
                    welcomeMessage.append("\n");
                    welcomeMessage.append(Lang.TEMPERATURE_HIGH.get());
                    if (GlobalWarming.getEconomy() != null) {
                        //Tip: create a bounty when the temperature is high:
                        welcomeMessage.append(Lang.TEMPERATURE_HIGHWITHBOUNTY.get());
                    }
                }

                //Send customized welcome message:
                gPlayer.sendMsg(welcomeMessage.toString());
            } else {
                //Notification that the climate engine was turned off:
                gPlayer.sendMsg(Lang.ENGINE_DISABLED);
            }
        }
    }

    /**
     * Show the top 10 polluters or planters as a chat message
     */
    private static void showTopTen(GPlayer gPlayer, boolean isPolluterList) {
        if (ClimateEngine.getInstance().isClimateEngineEnabled(gPlayer.getAssociatedWorldId())) {
            WorldClimateEngine associatedClimateEngine =
                  ClimateEngine.getInstance().getClimateEngine(gPlayer.getAssociatedWorldId());

            CarbonIndexModel indexModel = associatedClimateEngine.getCarbonIndexModel();
            ChatTable chatTable = new ChatTable(isPolluterList ? Lang.TOPTABLE_POLLUTERS.get() : Lang.TOPTABLE_PLANTERS.get());
            chatTable.setGridColor(isPolluterList ? ChatColor.DARK_RED : ChatColor.GREEN);
            chatTable.addHeader(Lang.TOPTABLE_PLAYER.get(), (int) (ChatTable.CHAT_WIDTH * 0.464));
            chatTable.addHeader(Lang.TOPTABLE_INDEX.get(), (int) (ChatTable.CHAT_WIDTH * 0.268));
            chatTable.addHeader(Lang.TOPTABLE_SCORE.get(), (int) (ChatTable.CHAT_WIDTH * 0.268));

            try {
                PlayerTable playerTable = GlobalWarming.getInstance().getTableManager().getPlayerTable();
                List<GPlayer> players = new ArrayList<>(playerTable.getPlayers().values());
                players.sort(Comparator.comparing(GPlayer::getCarbonScore));
                if (isPolluterList) {
                    Collections.reverse(players);
                }

                int rowCount = 0;
                for (GPlayer player : players) {
                    List<String> row = new ArrayList<>();
                    if (player.getUuid().equals(untrackedUUID)) {
                        continue;
                    }

                    int score = player.getCarbonScore();
                    double index = indexModel.getCarbonIndex(score);
                    row.add(Bukkit.getOfflinePlayer(player.getUuid()).getName());
                    row.add(formatIndex(index, score));
                    row.add(formatScore(score));
                    chatTable.addRow(row);
                    if (++rowCount == 10) {
                        break;
                    }
                }

                gPlayer.sendMsg(chatTable.toString());
            } catch (Exception e) {
                gPlayer.sendMsg(Lang.TOPTABLE_ERROR);
                e.printStackTrace();
            }
        } else {
            gPlayer.sendMsg(Lang.ENGINE_DISABLED);
        }
    }

    /**
     * Add an instructional booklet to a player's inventory
     * - Will prevent duplicates
     */
    public static void getBooklet(GPlayer gPlayer) {
        Player onlinePlayer = gPlayer.getOnlinePlayer();
        if (onlinePlayer != null) {
            //Prevent duplicates:
            // - Note that empty inventory slots will be NULL
            boolean isDuplicate = false;
            PlayerInventory inventory = onlinePlayer.getInventory();
            for(ItemStack item : inventory.getContents()) {
                if (item != null &&
                      item.getType().equals(Material.WRITTEN_BOOK) &&
                      item.getItemMeta().getDisplayName().equals(Lang.WIKI_NAME.get())) {
                    gPlayer.sendMsg(Lang.WIKI_ALREADYADDED);
                    isDuplicate = true;
                    break;
                }
            }

            //Add the booklet:
            if (!isDuplicate) {
                ItemStack wiki = new ItemStack(Material.WRITTEN_BOOK);
                final BookMeta meta = (BookMeta) wiki.getItemMeta();
                meta.setDisplayName(Lang.WIKI_NAME.get());
                meta.setAuthor(Lang.WIKI_AUTHOR.get());

                final ArrayList<String> lore = new ArrayList<>();
                lore.add(Lang.WIKI_LORE.get());
                meta.setLore(lore);

                final ArrayList<String> content = new ArrayList<>();
                content.add(Lang.WIKI_INTRODUCTION.get());
                content.add(Lang.WIKI_SCORES.get());
                content.add(Lang.WIKI_EFFECTS.get());
                content.add(Lang.WIKI_BOUNTY.get());
                content.add(Lang.WIKI_OTHER.get());

                //Create the book and add to inventory:
                meta.setPages(content);
                wiki.setItemMeta(meta);
                if (onlinePlayer.getInventory().addItem(wiki).isEmpty()) {
                    //Added:
                    gPlayer.sendMsg(Lang.WIKI_ADDED);
                } else {
                    //Inventory full:
                    gPlayer.sendMsg(Lang.GENERIC_INVENTORYFULL);
                }
            }
        }
    }
}
