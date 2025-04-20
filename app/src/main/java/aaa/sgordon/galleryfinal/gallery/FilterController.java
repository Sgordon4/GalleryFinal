package aaa.sgordon.galleryfinal.gallery;

import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.repository.caches.AttrCache;
import aaa.sgordon.galleryfinal.repository.caches.LinkCache;

public class FilterController {
	private final AttrCache attrCache;
	public final FilterRegistry registry;
	private final List<Predicate<ListItem>> extraQueryFilters;
	private final List<Predicate<ListItem>> extraTagFilters;

	public FilterController(FilterRegistry registry, AttrCache attrCache) {
		this.registry = registry;
		this.attrCache = attrCache;
		this.extraQueryFilters = new ArrayList<>();
		this.extraTagFilters = new ArrayList<>();
	}

	public void addExtraQueryFilter(Predicate<ListItem> predicate) {
		extraQueryFilters.add(predicate);
	}
	public void removeExtraQueryFilter(Predicate<ListItem> predicate) {
		extraQueryFilters.remove(predicate);
	}
	public void addExtraTagFilter(Predicate<ListItem> predicate) {
		extraTagFilters.add(predicate);
	}
	public void removeExtraTagFilter(Predicate<ListItem> predicate) {
		extraTagFilters.remove(predicate);
	}


	public void onListUpdated(List<ListItem> fileList) {
		Thread filter = new Thread(() -> {
			//Filter the list of files based on the current query
			List<ListItem> filtered = filterListByQuery(registry.activeQuery.getValue(), fileList, extraQueryFilters);
			filtered =  filterListByTags(registry.activeTags.getValue(), filtered, attrCache);
			registry.filteredList.postValue(filtered);
		});
		filter.start();
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


	public void onActiveQueryChanged(String newActiveQuery, List<ListItem> fullList) {
		registry.activeQuery.postValue(newActiveQuery);

		Thread filter = new Thread(() -> {
			List<ListItem> filtered = filterListByQuery(newActiveQuery, fullList, extraQueryFilters);
			filtered = filterListByTags(registry.activeTags.getValue(), filtered, attrCache);
			registry.filteredList.postValue(filtered);
		});
		filter.start();
	}

	public void onActiveTagsChanged(Set<String> newActiveTags, List<ListItem> fullList) {
		registry.activeTags.postValue(newActiveTags);

		Thread filter = new Thread(() -> {
			List<ListItem> filtered = filterListByQuery(registry.activeQuery.getValue(), fullList, extraQueryFilters);
			filtered = filterListByTags(newActiveTags, filtered, attrCache);
			registry.filteredList.postValue(filtered);
		});
		filter.start();
	}




	public void filter(List<ListItem> fullList) {
		Thread filter = new Thread(() -> {
			List<ListItem> filtered = filterListByQuery(registry.activeQuery.getValue(), fullList, extraQueryFilters);
			filtered = filterListByTags(registry.activeTags.getValue(), filtered, attrCache);
			registry.filteredList.postValue(filtered);
		});
		filter.start();
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
	public static List<ListItem> filterListByQuery(String filterQuery, List<ListItem> list, List<Predicate<ListItem>> extraQueryFilters) {

		//Consider any extra user defined filters
		Predicate<ListItem> extraFilter = extraQueryFilters.stream().reduce(s -> true, Predicate::and);
		list = list.stream().filter(extraFilter)
				.collect(Collectors.toList());

		if(filterQuery.isEmpty())
			return list;

		return list.stream().filter(item -> {
			//Make sure the fileName contains the query string
			return item.name.toLowerCase().contains(filterQuery.toLowerCase());
		}).collect(Collectors.toList());
	}

	public static List<ListItem> filterListByTags(Set<String> filterTags, List<ListItem> list, AttrCache attrCache) {
		if(filterTags.isEmpty())
			return list;

		return list.stream().filter(item -> {
			//If we're filtering for tags, make sure each item has all filtered tags

			if(LinkCache.isLinkEnd(item))
				return false;	//Exclude ends, since we can't reorder

			try {
				//Get the tags for the file. Since we have tags, if they have no tags filter them out
				JsonObject attrs = attrCache.getAttr(item.fileUID);
				if(attrs == null) return false;
				JsonArray fileTags = attrs.getAsJsonArray("tags");
				if(fileTags == null)  return false;

				//Check if any of the tags we're searching for are contained in the file's tags
				for(JsonElement tag : fileTags) {
					if(filterTags.contains(tag.getAsString())) {
						return true;
					}
				}
			} catch (FileNotFoundException | ConnectException e) {
				//Skip
			}

			return false;
		}).collect(Collectors.toList());
	}


	//---------------------------------------------------------------------------------------------

	public static class FilterRegistry {
		public final MutableLiveData< List<ListItem> > filteredList;

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
