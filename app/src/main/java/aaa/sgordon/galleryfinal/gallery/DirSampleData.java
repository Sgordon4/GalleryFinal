package aaa.sgordon.galleryfinal.gallery;

import android.content.Context;
import android.util.Pair;

import androidx.room.Room;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.repository.local.LocalRepo;
import aaa.sgordon.galleryfinal.repository.local.database.LocalDatabase;

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



	public static UUID setupDatabase(Context context) throws FileNotFoundException {
		LocalDatabase db = Room.inMemoryDatabaseBuilder(context, LocalDatabase.class).build();
		LocalRepo.initialize(db, context.getCacheDir().toString());
		HybridAPI hapi = HybridAPI.getInstance();

		//Fake creating the account
		UUID currentAccount = UUID.randomUUID();
		hapi.setAccount(currentAccount);

		//Create the root directory for the new account
		UUID root = hapi.createFile(currentAccount, true, false);

		//Setup files in the root directory
		Pair<UUID, String> r_l1 = new Pair<>(hapi.createFile(currentAccount, true, true), "Root link to dir 1");
		Pair<UUID, String> r_d1 = new Pair<>(hapi.createFile(currentAccount, true, false), "Root dir 1");
		Pair<UUID, String> r_f1 = new Pair<>(hapi.createFile(currentAccount, false, false), "Root file 1");
		Pair<UUID, String> r_f2 = new Pair<>(hapi.createFile(currentAccount, false, false), "Root file 2");
		Pair<UUID, String> r_f3 = new Pair<>(hapi.createFile(currentAccount, false, false), "Root file 3");
		Pair<UUID, String> r_l2 = new Pair<>(hapi.createFile(currentAccount, true, true), "Root link to link to sideDir");
		Pair<UUID, String> r_l3 = new Pair<>(hapi.createFile(currentAccount, true, true), "Root link to sideDir");
		Pair<UUID, String> r_f4 = new Pair<>(hapi.createFile(currentAccount, false, false), "Root file 4");
		Pair<UUID, String> r_f5 = new Pair<>(hapi.createFile(currentAccount, false, false), "Root file 5");
		List<Pair<UUID, String>> rootList = new ArrayList<>(Arrays.asList(r_l1, r_d1, r_f1, r_f2, r_f3, r_l2, r_l3, r_f4, r_f5));

		Pair<UUID, String> sideDir = new Pair<>(hapi.createFile(currentAccount, true, false), "Side dir");

		/*
		System.out.println("Root: "+root);
		System.out.println("Dir1: "+r_d1.first);
		System.out.println("Dir2: "+sideDir.first);
		System.out.println("Link1: "+r_l1.first);
		System.out.println("Link2: "+r_l2.first);
		System.out.println("Link3: "+r_l3.first);
		 */


		//Link r_l1 to r_d1
		try {
			hapi.lockLocal(r_l1.first);
			hapi.writeFile(r_l1.first, r_d1.first.toString().getBytes(), HFile.defaultChecksum);
		} finally { hapi.unlockLocal(r_l1.first); }

		//Link r_l2 to r_l3
		try {
			hapi.lockLocal(r_l2.first);
			hapi.writeFile(r_l2.first, r_l3.first.toString().getBytes(), HFile.defaultChecksum);
		} finally { hapi.unlockLocal(r_l2.first); }

		//Link r_l3 to sideDir
		try {
			hapi.lockLocal(r_l3.first);
			hapi.writeFile(r_l3.first, sideDir.first.toString().getBytes(), HFile.defaultChecksum);
		} finally { hapi.unlockLocal(r_l3.first); }

		//Write the list to the root directory
		List<String> rootLines = rootList.stream().map(pair -> pair.first+" "+pair.second)
				.collect(Collectors.toList());
		byte[] newContent = String.join("\n", rootLines).getBytes();
		try {
			hapi.lockLocal(root);
			hapi.writeFile(root, newContent, HFile.defaultChecksum);
		} finally { hapi.unlockLocal(root); }

		//------------------------------------------------------

		//Setup files in Root - Dir 1
		Pair<UUID, String> r_d1_f1 = new Pair<>(hapi.createFile(currentAccount, false, false), "- D1 file 1");
		Pair<UUID, String> r_d1_f2 = new Pair<>(hapi.createFile(currentAccount, false, false), "- D1 file 2");
		Pair<UUID, String> r_d1_f3 = new Pair<>(hapi.createFile(currentAccount, false, false), "- D1 file 3");
		List<Pair<UUID, String>> r_d1_List = new ArrayList<>(Arrays.asList(r_d1_f1, r_d1_f2, r_d1_f3));

		//Write the list to dir 1
		List<String> dir1Lines = r_d1_List.stream().map(pair -> pair.first+" "+pair.second)
				.collect(Collectors.toList());
		newContent = String.join("\n", dir1Lines).getBytes();
		try {
			hapi.lockLocal(r_d1.first);
			hapi.writeFile(r_d1.first, newContent, HFile.defaultChecksum);
		} finally { hapi.unlockLocal(r_d1.first); }

		//------------------------------------------------------

		//Setup files in sideDir
		Pair<UUID, String> r_d2_f1 = new Pair<>(hapi.createFile(currentAccount, false, false), "- SideDir file 1");
		Pair<UUID, String> r_d2_f2 = new Pair<>(hapi.createFile(currentAccount, false, false), "- SideDir file 2");
		Pair<UUID, String> r_d2_f3 = new Pair<>(hapi.createFile(currentAccount, false, false), "- SideDir file 3");
		Pair<UUID, String> r_d2_f4 = new Pair<>(hapi.createFile(currentAccount, false, false), "- SideDir file 4");
		Pair<UUID, String> r_d2_f5 = new Pair<>(hapi.createFile(currentAccount, false, false), "- SideDir file 5");
		List<Pair<UUID, String>> sideDir_List = new ArrayList<>(Arrays.asList(r_d2_f1, r_d2_f2, r_d2_f3, r_d2_f4, r_d2_f5));

		Pair<UUID, String> r_l3_again = new Pair<>(r_l3.first, "- SideDir "+r_l3.second+" again");
		sideDir_List.add(r_l3_again);		//Add a link that links to this dir to test traversal cancel

		//Write the list to sideDir
		List<String> dir2Lines = sideDir_List.stream().map(pair -> pair.first+" "+pair.second)
				.collect(Collectors.toList());
		newContent = String.join("\n", dir2Lines).getBytes();
		try {
			hapi.lockLocal(sideDir.first);
			hapi.writeFile(sideDir.first, newContent, HFile.defaultChecksum);
		} finally { hapi.unlockLocal(sideDir.first); }


		return root;
	}
}
