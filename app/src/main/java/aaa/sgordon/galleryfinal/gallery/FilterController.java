package aaa.sgordon.galleryfinal.gallery;

import android.util.Pair;

import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class FilterController {
	private final AttrCache attrCache;
	public final FilterRegistry registry;
	public FilterController(FilterRegistry registry, AttrCache attrCache) {
		this.registry = registry;
		this.attrCache = attrCache;
	}


	public void onListUpdated(List<Pair<Path, String>> fileList) {
		//Filter the list of files based on the current query
		List<Pair<Path, String>> filtered = filterListByQuery(registry.activeQuery.getValue(), fileList);
		filtered =  filterListByTags(registry.activeTags.getValue(), filtered, attrCache);
		registry.filteredList.postValue(filtered);
	}

	public void onTagsUpdated(Map<String, Set<UUID>> newTags) {
		Map<String, Set<UUID>> filteredTags = filterTagsByQuery(registry.query, newTags);
		registry.filteredTags.postValue(filteredTags);

		//Remove any active tags that have vanished from the list of tags
		Set<String> active = registry.activeTags.getValue();
		active.retainAll(newTags.keySet());
		registry.activeTags.postValue(active);
	}



	public void onQueryChanged(String newQuery, Map<String, Set<UUID>> fullTags) {
		registry.query = newQuery;

		Map<String, Set<UUID>> filteredTags = filterTagsByQuery(registry.query, fullTags);
		registry.filteredTags.postValue(filteredTags);
	}


	public void onActiveQueryChanged(String newActiveQuery, List<Pair<Path, String>> fullList) {
		registry.activeQuery.postValue(newActiveQuery);

		List<Pair<Path, String>> filtered = filterListByQuery(newActiveQuery, fullList);
		filtered = filterListByTags(registry.activeTags.getValue(), filtered, attrCache);
		registry.filteredList.postValue(filtered);
	}

	public void onActiveTagsChanged(Set<String> newActiveTags, List<Pair<Path, String>> fullList) {
		registry.activeTags.postValue(newActiveTags);

		List<Pair<Path, String>> filtered = filterListByQuery(registry.activeQuery.getValue(), fullList);
		filtered = filterListByTags(newActiveTags, filtered, attrCache);
		registry.filteredList.postValue(filtered);
	}




	private Map<String, Set<UUID>> filterTagsByQuery(String query, Map<String, Set<UUID>> tags) {
		Map<String, Set<UUID>> filtered = new HashMap<>();
		for(Map.Entry<String, Set<UUID>> entry : tags.entrySet()) {
			if(entry.getKey().contains(query))
				filtered.put(entry.getKey(), entry.getValue());
		}
		return filtered;
	}


	//Take the list and filter out anything that doesn't match our filters (name and tags)
	public static List<Pair<Path, String>> filterListByQuery(String filterQuery, List<Pair<Path, String>> list) {
		if(filterQuery.isEmpty())
			return list;

		return list.stream().filter(pathStringPair -> {
			//Make sure the fileName contains the query string
			String fileName = pathStringPair.second;
			return fileName.toLowerCase().contains(filterQuery.toLowerCase());
		}).collect(Collectors.toList());
	}

	public static List<Pair<Path, String>> filterListByTags(Set<String> filterTags, List<Pair<Path, String>> list, AttrCache attrCache) {
		if(filterTags.isEmpty())
			return list;

		return list.stream().filter(pathStringPair -> {
			//If we're filtering for tags, make sure each item has all filtered tags
			//Get the UUID of the file from the path
			Path path = pathStringPair.first;
			String UUIDString = path.getFileName().toString();
			if(UUIDString.equals("END"))
				return false;	//Exclude ends, since we can't reorder
			//	UUIDString = path.getParent().getFileName().toString();
			UUID thisFileUID = UUID.fromString(UUIDString);

			try {
				//Get the tags for the file. Since we have tags, if they have no tags filter them out
				JsonObject attrs = attrCache.getAttr(thisFileUID);
				if(attrs == null) return false;
				JsonArray fileTags = attrs.getAsJsonArray("tags");
				if(fileTags == null)  return false;

				//Check if any of the tags we're searching for are contained in the file's tags
				for(JsonElement tag : fileTags) {
					if(filterTags.contains(tag.getAsString())) {
						return true;
					}
				}
			} catch (FileNotFoundException e) {
				//Skip
			}

			return false;
		}).collect(Collectors.toList());
	}


	//---------------------------------------------------------------------------------------------

	public static class FilterRegistry {
		public final MutableLiveData< List<Pair<Path, String>> > filteredList;

		public final MutableLiveData< Map<String, Set<UUID>> > filteredTags;

		//Query is the current string in the searchView, activeQuery is the one that was last submitted
		public String query;
		public final MutableLiveData<String> activeQuery;
		public final MutableLiveData< Set<String> > activeTags;


		public FilterRegistry() {
			this.query = "";

			this.activeQuery = new MutableLiveData<>();
			this.activeQuery.setValue("");
			this.activeTags = new MutableLiveData<>();
			this.activeTags.setValue(new HashSet<>());

			this.filteredList = new MutableLiveData<>();
			this.filteredList.setValue(new ArrayList<>());
			this.filteredTags = new MutableLiveData<>();
			this.filteredTags.setValue(new HashMap<>());
		}
	}
}
