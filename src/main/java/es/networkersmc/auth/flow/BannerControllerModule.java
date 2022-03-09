package es.networkersmc.auth.flow;

import com.google.common.base.Preconditions;
import es.networkersmc.auth.session.AuthSession;
import es.networkersmc.dendera.language.Language;
import es.networkersmc.dendera.module.Module;
import es.networkersmc.dendera.util.bukkit.map.ImageBanner;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Named;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

// Bukkit-synced class, methods should be use synchronously.
public class BannerControllerModule implements Module, Listener {

    @Inject private @Named("banners-directory") File bannersDirectory;
    @Inject private Server server;

    private ImageBanner banner;
    private ArmorStand lock;

    @Override
    public void onStart() {
        this.setupBanner();
    }

    public void onJoin(Player player, AuthSession session) {
        Language language = session.getUser().getLanguage();

        BannerImage bannerImage;
        switch (session.getState()) {
            case LOGIN:
                bannerImage = BannerImage.LOGIN;
                break;
            case REGISTER:
                bannerImage = BannerImage.REGISTER;
                break;
            case CHANGE_PASSWORD:
                bannerImage = BannerImage.CHANGE_PASSWORD;
                break;
            default: // This should never happen
                player.kickPlayer("There was an error. Please contact with us."); // TODO: ERROR MESSAGE (Dendera)
                throw new IllegalStateException("Session state on join wasn't expected: " + session.getState());
        }

        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(lock);
        this.updateBanner(player, language, bannerImage);
    }

    public void onLoginSuccess(Player player) {
        this.playSound(player, Sound.ORB_PICKUP, 0);
        // TODO: Maybe "loading" banner?
    }

    public void onLoginFailure(Player player, AuthSession session) {
        Language language = session.getUser().getLanguage();

        this.playSound(player, Sound.NOTE_BASS, 0);
        this.updateBanner(player, language, BannerImage.LOGIN_WRONG_PASSWORD);
    }

    public void onPasswordInput(Player player, AuthSession session) {
        Language language = session.getUser().getLanguage();

        this.playSound(player, Sound.ORB_PICKUP, 0);
        this.updateBanner(player, language, BannerImage.CONFIRM_PASSWORD);
    }

    public void onPasswordInputError(Player player, AuthSession session, boolean isChangingPassword) {
        Language language = session.getUser().getLanguage();
        BannerImage bannerImage = isChangingPassword
                ? BannerImage.CHANGE_PASSWORD_PASSWORDS_DONT_MATCH
                : BannerImage.REGISTER_PASSWORDS_DONT_MATCH;

        this.playSound(player, Sound.NOTE_BASS, 0);
        this.updateBanner(player, language, bannerImage);
    }

    private void setupBanner() {
        World world = server.getWorlds().get(0);

        // Already calculated for all FOVs and aspect ratios
        Location spawnLocation = new Location(world, 24.0, 113.0, 7.0, 0, 180);
        banner = new ImageBanner(world, 16 * 3, 8 * 3);
        banner.place(world.getBlockAt(0, 100, 0), BlockFace.SOUTH);

        lock = world.spawn(spawnLocation, ArmorStand.class);
        lock.setVisible(false);
        lock.setGravity(false);
    }

    private void playSound(Player player, Sound sound, float pitch) {
        player.playSound(player.getLocation(), sound, 1, pitch);
    }

    private void updateBanner(Player player, Language language, BannerImage bannerImage) {
        File bannerFile = new File(bannersDirectory, language.getCode() + "-" + bannerImage.toString());

        if (!bannerFile.exists()) {
            Language fallback = Language.getFallback(language);
            Preconditions.checkArgument(fallback != null); // This should never happen as all languages redirect to en_US, and there should be a file for it
            this.updateBanner(player, fallback, bannerImage);
            return;
        }

        try {
            BufferedImage image = ImageIO.read(bannerFile);
            this.banner.draw(player, image);
        } catch (IOException e) {
            e.printStackTrace();
            player.kickPlayer("There was an error. Please try again."); // TODO: ERROR MESSAGE (Dendera)
        }
    }

    @EventHandler
    public void onLeaveLock(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            event.setCancelled(true);
            event.getPlayer().setSpectatorTarget(lock);
        }
    }
}