package me.armar.plugins.autorank.util.uuid;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Manages everything related to UUIDs
 * <p>
 * Date created: 17:13:57 2 apr. 2014
 * 
 * @author Staartvin
 * 
 */
public class UUIDManager {

	private static Map<String, UUID> foundUUIDs = new HashMap<String, UUID>();
	private static Map<UUID, String> foundPlayers = new HashMap<UUID, String>();

	// This hashmap stores the cached values of uuids for players. 
	private static HashMap<String, UUID> cachedUUIDs = new HashMap<String, UUID>();

	// This hashmap stores what the time is that of the latest cache for a certain player.
	// This is used to see if the cached UUID was older than 12 hours. If it is older than 12 hours,
	// it will be renewed.
	private static HashMap<String, Long> lastCached = new HashMap<String, Long>();

	// This the time that one cached value is valid (in hours).
	private static int maxLifeTime = 12;

	public static Map<String, UUID> getUUIDs(final List<String> names) {

		// Clear maps first
		foundUUIDs.clear();

		// A new map to store cached values
		HashMap<String, UUID> uuids = new HashMap<String, UUID>();

		// This is used to check if we need to use the lookup from the mojang website.
		boolean useInternetLookup = true;

		// Check if we have cached values
		for (String playerName : names) {

			// If cached value is still valid, use it.
			if (!shouldUpdateValue(playerName)) {
				//System.out.print("Using cached value of uuid for " + playerName);
				uuids.put(playerName, getCachedUUID(playerName));
			}
		}

		// All names were retrieved from cached values
		// So we don't need to do a lookup to the Mojang website.
		if (uuids.entrySet().size() == names.size()) {
			useInternetLookup = false;
		}

		// No internet lookup needed.
		if (!useInternetLookup) {
			// Return all cached values.
			return uuids;
		}

		// From here on we know that didn't have all uuids as cached values.
		// So we need to do a lookup.
		// We have to make sure we only lookup the players that we haven't got cached values of yet.

		// Remove players that don't need to be looked up anymore. 
		// Just for performance sake.
		for (Entry<String, UUID> entry : uuids.entrySet()) {
			names.remove(entry.getKey());
		}

		// Now we need to lookup the other players

		Thread fetcherThread = new Thread(new Runnable() {

			public void run() {
				UUIDFetcher fetcher = new UUIDFetcher(names);

				Map<String, UUID> response = null;

				try {
					response = fetcher.call();
				} catch (Exception e) {
					if (e instanceof IOException) {
						Bukkit.getLogger()
								.warning(
										"Tried to contact Mojang page for UUID lookup but failed.");
						return;
					}
					e.printStackTrace();
				}

				if (response != null) {
					foundUUIDs = response;
				}
			}
		});

		fetcherThread.start();

		if (fetcherThread.isAlive()) {
			try {
				fetcherThread.join();
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
		}

		// Update cached entries
		for (Entry<String, UUID> entry : foundUUIDs.entrySet()) {
			String playerName = entry.getKey();
			UUID uuid = entry.getValue();

			// Add found uuids to the list of uuids to return
			uuids.put(playerName, uuid);

			if (shouldUpdateValue(playerName)) {
				// Update cached values
				addCachedPlayer(playerName, uuid);
			} else {
				// Do not update if it is not needed.
				continue;
			}
		}

		// Thread stopped now, collect results
		return uuids;
	}

	public static Map<UUID, String> getPlayers(final List<UUID> uuids) {
		// Clear names first
		foundPlayers.clear();

		// A new map to store cached values
		HashMap<UUID, String> players = new HashMap<UUID, String>();

		// This is used to check if we need to use the lookup from the mojang website.
		boolean useInternetLookup = true;

		// Check if we have cached values
		for (UUID uuid : uuids) {

			String playerName = null;

			for (Entry<String, UUID> entry : cachedUUIDs.entrySet()) {
				if (entry.getValue().equals(uuid)) {
					playerName = entry.getKey();
				}
			}

			if (playerName != null) {
				// If cached value is still valid, use it.
				if (!shouldUpdateValue(playerName)) {
					//System.out.print("Using cached value of playername for " + playerName);
					players.put(uuid, playerName);
				}
			}
		}

		// All names were retrieved from cached values
		// So we don't need to do a lookup to the Mojang website.
		if (players.entrySet().size() == uuids.size()) {
			useInternetLookup = false;
		}

		// No internet lookup needed.
		if (!useInternetLookup) {
			// Return all cached values.
			return players;
		}

		// From here on we know that didn't have all uuids as cached values.
		// So we need to do a lookup.
		// We have to make sure we only lookup the players that we haven't got cached values of yet.

		// Remove uuids that don't need to be looked up anymore. 
		// Just for performance sake.
		for (UUID entry : players.keySet()) {
			uuids.remove(entry);
		}

		// Now we need to lookup the other players

		Thread fetcherThread = new Thread(new Runnable() {

			public void run() {
				NameFetcher fetcher = new NameFetcher(uuids);

				Map<UUID, String> response = null;

				try {
					response = fetcher.call();
				} catch (Exception e) {
					if (e instanceof IOException) {
						Bukkit.getLogger()
								.warning(
										"Tried to contact Mojang page for UUID lookup but failed.");
						return;
					}
					e.printStackTrace();
				}

				if (response != null) {
					foundPlayers = response;
				}
			}
		});

		fetcherThread.start();

		if (fetcherThread.isAlive()) {
			try {
				fetcherThread.join();
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
		}

		// Update cached entries
		for (Entry<UUID, String> entry : foundPlayers.entrySet()) {
			String playerName = entry.getValue();
			UUID uuid = entry.getKey();

			// Add found players to the list of players to return
			players.put(uuid, playerName);

			if (shouldUpdateValue(playerName)) {
				// Update cached values
				addCachedPlayer(playerName, uuid);
			} else {
				// Do not update if it is not needed.
				continue;
			}
		}

		// Thread stopped now, collect results
		return players;
	}

	/**
	 * Get the Minecraft name of the player that is hooked to this Mojang
	 * account UUID.
	 * 
	 * @param uuid the UUID of the Mojang account
	 * @return the name of player or null if not found.
	 */
	public static String getPlayerFromUUID(UUID uuid) {
		if (uuid == null)
			return null;

		Map<UUID, String> players = getPlayers(Arrays.asList(uuid));

		if (players == null)
			return null;

		if (players.isEmpty())
			return null;

		// Search case insensitive
		//for (Entry<UUID, String> entry : players.entrySet()) {
		//	if (entry.getKey().toString().equals(uuid.toString())) {
		//		return entry.getValue();
		//	}
		//}
		
		return players.get(uuid);
	}

	/**
	 * Get the UUID of the Mojang account associated with this player name
	 * 
	 * @param playerName Name of the player
	 * @return UUID of the associated Mojang account or null if not found.
	 */
	public static UUID getUUIDFromPlayer(String playerName) {
		if (playerName == null) {
			return null;
		}

		Map<String, UUID> uuids = getUUIDs(Arrays.asList(playerName));

		if (uuids == null) {
			return null;
		}

		if (uuids.isEmpty()) {
			return null;
		}

		// Search case insensitive
		for (Entry<String, UUID> entry : uuids.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(playerName)) {
				return entry.getValue();
			}
		}

		return null;
	}

	private static boolean shouldUpdateValue(String playerName) {

		// Incorrectly cached, so cache now.
		if (!isLastCached(playerName) || !isCachedUUID(playerName))
			return true;

		long lastCacheTime = getLastCached(playerName);

		// No cache time
		if (lastCacheTime <= 0) {
			return true;
		}

		long currentTime = System.currentTimeMillis();

		long lifeTime = currentTime - lastCacheTime;

		// The cached value is older than it ought to be.
		if ((lifeTime / 3600000) > maxLifeTime) {
			return true;
		}

		return false;
	}

	private static UUID getCachedUUID(String playerName) {
		// Already found
		if (cachedUUIDs.containsKey(playerName))
			return cachedUUIDs.get(playerName);

		// Search for lowercase matches
		for (String loggedName : cachedUUIDs.keySet()) {
			if (loggedName.equalsIgnoreCase(playerName)) {
				playerName = loggedName;
				break;
			}
		}

		if (!cachedUUIDs.containsKey(playerName))
			return null;

		// Grab UUID
		return cachedUUIDs.get(playerName);
	}

	private static long getLastCached(String playerName) {
		// Already found
		if (lastCached.containsKey(playerName))
			return lastCached.get(playerName);

		// Search for lowercase matches
		for (String loggedName : lastCached.keySet()) {
			if (loggedName.equalsIgnoreCase(playerName)) {
				playerName = loggedName;
				break;
			}
		}

		if (!lastCached.containsKey(playerName))
			return -1;

		// Grab last changed
		return lastCached.get(playerName);
	}

	private static boolean isCachedUUID(String playerName) {
		return getCachedUUID(playerName) != null;
	}

	private static boolean isLastCached(String playerName) {
		return getLastCached(playerName) > 0;
	}

	public static void addCachedPlayer(Player player) {
		// Do not update if we still have one that is valid
		if (!shouldUpdateValue(player.getName()))
			return;

		addCachedPlayer(player.getName(), player.getUniqueId());
	}

	private static void addCachedPlayer(String playerName, UUID uuid) {
		//System.out.print("Cached " + playerName + " with UUID " + uuid.toString());

		cachedUUIDs.put(playerName, uuid);
		lastCached.put(playerName, System.currentTimeMillis());
	}
}
