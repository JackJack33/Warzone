package com.minehut.tgm.modules.dtm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minehut.tgm.TGM;
import com.minehut.tgm.match.Match;
import com.minehut.tgm.match.MatchModule;
import com.minehut.tgm.modules.monument.Monument;
import com.minehut.tgm.modules.monument.MonumentService;
import com.minehut.tgm.modules.region.Region;
import com.minehut.tgm.modules.region.RegionManagerModule;
import com.minehut.tgm.modules.scoreboard.ScoreboardInitEvent;
import com.minehut.tgm.modules.scoreboard.ScoreboardManagerModule;
import com.minehut.tgm.modules.scoreboard.SimpleScoreboard;
import com.minehut.tgm.modules.team.MatchTeam;
import com.minehut.tgm.modules.team.TeamManagerModule;
import com.minehut.tgm.util.ColorConverter;
import com.minehut.tgm.util.FireworkUtil;
import com.minehut.tgm.util.Parser;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class DTMModule extends MatchModule implements Listener {
    @Getter
    private final List<Monument> monuments = new ArrayList<>();

    @Getter
    private final HashMap<Monument, List<Integer>> monumentScoreboardLines = new HashMap<>();

    @Override
    public void load(Match match) {
        JsonObject dtmJson = match.getMapContainer().getMapInfo().getJsonObject().get("dtm").getAsJsonObject();

        for (JsonElement monumentElement : dtmJson.getAsJsonArray("monuments")) {
            JsonObject monumentJson = monumentElement.getAsJsonObject();

            String name = monumentJson.get("name").getAsString();
            Region region = match.getModule(RegionManagerModule.class).getRegion(match, monumentJson.get("region"));
            List<MatchTeam> teams = Parser.getTeamsFromElement(match.getModule(TeamManagerModule.class), monumentJson.get("teams"));
            List<Material> materials = Parser.getMaterialsFromElement(monumentJson.get("materials"));
            int health = monumentJson.get("health").getAsInt();

            monuments.add(new Monument(name, teams, region, materials, health, health));
        }

        TeamManagerModule teamManagerModule = TGM.get().getModule(TeamManagerModule.class);

        //monument services
        for (Monument monument : monuments) {
            monument.addService(new MonumentService() {
                @Override
                public void damage(Player player, Block block) {
                    updateOnScoreboard(monument);

                    MatchTeam matchTeam = teamManagerModule.getTeam(player);
                    Bukkit.broadcastMessage(matchTeam.getColor() + player.getName() + ChatColor.WHITE + " damaged " + monument.getOwners().get(0).getColor() + ChatColor.BOLD + monument.getName());
                    playFireworkEffect(matchTeam.getColor(), block.getLocation());
                }

                @Override
                public void destroy(Player player, Block block) {
                    updateOnScoreboard(monument);

                    MatchTeam matchTeam = teamManagerModule.getTeam(player);

                    Bukkit.broadcastMessage(matchTeam.getColor() + player.getName() + ChatColor.WHITE + " destroyed " + monument.getOwners().get(0).getColor() + ChatColor.BOLD + monument.getName());
                    playFireworkEffect(matchTeam.getColor(), block.getLocation());

                    TGM.get().getMatchManager().endMatch(matchTeam);
                }
            });
        }

        //load monuments
        for (Monument monument : monuments) {
            monument.load();
        }
    }

    private void playFireworkEffect(ChatColor color, Location location) {
        FireworkUtil.spawnFirework(location, FireworkEffect.builder()
                .with(FireworkEffect.Type.BURST)
                .withFlicker()
                .withColor(ColorConverter.getColor(color))
                .build(), 0).detonate();

        // Play the sound for the player if they are too far to render the firework.
        for(Player listener : Bukkit.getOnlinePlayers()) {
            if(listener.getLocation().distance(location) > 64) {
                listener.playSound(listener.getLocation(), Sound.ENTITY_FIREWORK_BLAST, 0.75f, 1f);
                listener.playSound(listener.getLocation(), Sound.ENTITY_FIREWORK_TWINKLE, 0.75f, 1f);
            }
        }
    }

    @EventHandler
    public void onScoreboardInit(ScoreboardInitEvent event) {
        List<MatchTeam> teams = TGM.get().getModule(TeamManagerModule.class).getTeams();

        int i = 0;
        for (MatchTeam matchTeam : teams) {
            if(matchTeam.isSpectator()) continue;

            for (Monument monument : monuments) {
                if (monument.getOwners().contains(matchTeam)) {
                    if (monumentScoreboardLines.containsKey(monument)) {
                        monumentScoreboardLines.get(monument).add(i);
                    } else {
                        monumentScoreboardLines.put(monument, Arrays.asList(i));
                    }

                    event.getSimpleScoreboard().add(getScoreboardLine(monument), i);

                    i++;
                }
            }
            event.getSimpleScoreboard().add(matchTeam.getColor() + matchTeam.getAlias(), i);
            i++;
        }
    }

    private void updateOnScoreboard(Monument monument) {
        ScoreboardManagerModule scoreboardManagerModule = TGM.get().getModule(ScoreboardManagerModule.class);

        for (int i : monumentScoreboardLines.get(monument)) {
            for (SimpleScoreboard simpleScoreboard : scoreboardManagerModule.getScoreboards().values()) {
                simpleScoreboard.remove(i);
                simpleScoreboard.add(getScoreboardLine(monument), i);
                simpleScoreboard.update();
            }
        }
    }

    private String getScoreboardLine(Monument monument) {
        if (monument.isAlive()) {
            int percentage = monument.getHealthPercentage();

            if (percentage > 70) {
                return "  " + ChatColor.GREEN.toString() + percentage + "% " + ChatColor.WHITE + monument.getName();
            } else if (percentage > 40) {
                return "  " + ChatColor.YELLOW.toString() + percentage + "% " + ChatColor.WHITE + monument.getName();
            } else {
                return "  " + ChatColor.RED.toString() + percentage + "% " + ChatColor.WHITE + monument.getName();
            }
        } else {
            return "  " + ChatColor.STRIKETHROUGH + monument.getName();
        }
    }

    public List<Monument> getAliveMonuments(MatchTeam matchTeam) {
        List<Monument> alive = new ArrayList<>();
        for (Monument monument : monuments) {
            if (monument.isAlive() && monument.getOwners().contains(matchTeam)) {
                alive.add(monument);
            }
        }
        return alive;
    }

    @Override
    public void unload() {
        for (Monument monument : monuments) {
            monument.unload();
        }
    }
}