package aaa.sgordon.galleryfinal.gallery;

import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DirSampleData {
	Map<UUID, List<Pair<UUID, String>>> dirMap;

	UUID rootDir;
	UUID l1_1 = UUID.fromString("01649af9-109a-429e-b095-d9165214f415");
	UUID l1_2 = UUID.fromString("7e7848a9-a7f8-4e7c-b1f2-826bac8d8a35");
	UUID l2_1 = UUID.fromString("555c0c0c-12fb-4d89-be9a-73583d606b5f");


	public DirSampleData(UUID rootUUID) {
		dirMap = new HashMap<>();

		this.rootDir = rootUUID;

		List<Pair<UUID, String>> rootList = new ArrayList<>();
		rootList.add(new Pair<>(l1_1, "Link to Dir L1_1"));
		rootList.add(new Pair<>(UUID.randomUUID(), "Root file 1"));
		rootList.add(new Pair<>(UUID.randomUUID(), "Root file 2"));
		rootList.add(new Pair<>(UUID.randomUUID(), "Root file 3"));
		rootList.add(new Pair<>(l1_2, "Link to Dir L1_2"));
		rootList.add(new Pair<>(UUID.randomUUID(), "Root file 4"));
		rootList.add(new Pair<>(UUID.randomUUID(), "Root file 5"));


		List<Pair<UUID, String>> l1_1_List = new ArrayList<>();
		l1_1_List.add(new Pair<>(UUID.randomUUID(), "- L1_1 file 1"));
		l1_1_List.add(new Pair<>(UUID.randomUUID(), "- L1_1 file 2"));
		l1_1_List.add(new Pair<>(UUID.randomUUID(), "- L1_1 file 3"));
		l1_1_List.add(new Pair<>(UUID.randomUUID(), "- L1_1 file 4"));


		List<Pair<UUID, String>> l1_2_List = new ArrayList<>();
		l1_2_List.add(new Pair<>(UUID.randomUUID(), "- L1_2 file 1"));
		l1_2_List.add(new Pair<>(l2_1, "- Link to Dir L2_1"));
		l1_2_List.add(new Pair<>(UUID.randomUUID(), "- L1_2 file 2"));
		l1_2_List.add(new Pair<>(UUID.randomUUID(), "- L1_2 file 3"));
		l1_2_List.add(new Pair<>(UUID.randomUUID(), "- L1_2 file 4"));


		List<Pair<UUID, String>> l2_1_list = new ArrayList<>();
		l2_1_list.add(new Pair<>(UUID.randomUUID(), "- - l2_1 file 1"));
		l2_1_list.add(new Pair<>(UUID.randomUUID(), "- - l2_1 file 2"));
		l2_1_list.add(new Pair<>(rootUUID, "Link to Root! Make sure we aren't looping!"));
		l2_1_list.add(new Pair<>(UUID.randomUUID(), "- - l2_1 file 3"));


		dirMap.put(rootDir, rootList);
		dirMap.put(l1_1, l1_1_List);
		dirMap.put(l1_2, l1_2_List);
		dirMap.put(l2_1, l2_1_list);
	}

	public boolean isLinkToDir(UUID uuid) {
		return uuid.equals(rootDir) || uuid.equals(l1_1) || uuid.equals(l1_2) || uuid.equals(l2_1);
	}
}
