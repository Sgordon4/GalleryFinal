package aaa.sgordon.galleryfinal.utilities;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.room.Room;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.R;
import aaa.sgordon.galleryfinal.repository.gallery.DirItem;
import aaa.sgordon.galleryfinal.repository.gallery.link.ExternalTarget;
import aaa.sgordon.galleryfinal.repository.gallery.link.InternalTarget;
import aaa.sgordon.galleryfinal.repository.galleryhelpers.MainStorageHandler;
import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.repository.local.database.LocalDatabase;

public class DirSampleData {
	private static final String TAG = "Gal.Sample";

	private static final Uri externalUri_Jpg_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");
	private static final String externalUri_Jpg_1MB_Checksum = "35C461DEE98AAD4739707C6CCA5D251A1617BFD928E154995CA6F4CE8156CFFC";

	private static final Uri externalUri_Gif_40KB = Uri.parse("https://sample-videos.com/gif/3.gif");
	private static final String externalUri_Gif_40KB_Checksum= "0FF064BA36E4F493F6A1B3D9D29C8EEE1B719E39FC6768C5A6129534869C380B";

	//private static final Uri externalUri_MP4_1MB = Uri.parse("https://sample-videos.com/video321/mp4/720/big_buck_bunny_720p_1mb.mp4");
	private static final Uri externalUri_MP4_1MB = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4");
	private static final String externalUri_MP4_1MB_Checksum= "F25B31F155970C46300934BDA4A76CD2F581ACAB45C49762832FFDFDDBCF9FDD";



	public static UUID setupDatabaseSmall(Context context) throws FileNotFoundException, IOException {
		Log.i(TAG, "Setting up SMALL in-memory database...");
		LocalDatabase db = Room.inMemoryDatabaseBuilder(context, LocalDatabase.class).build();
		Uri storageDir = MainStorageHandler.getStorageTreeUri(context);
		if(storageDir == null) throw new RuntimeException("Storage directory is null!");
		//HybridAPI.initialize(db, context.getCacheDir().toString());
		HybridAPI.initialize(db, storageDir);
		HybridAPI hapi = HybridAPI.getInstance();

		//Fake creating the account
		UUID currentAccount = UUID.randomUUID();
		hapi.setAccount(currentAccount);

		//Create the root directory for the new account
		UUID root = hapi.createFile(currentAccount, true, false);

		//Setup files in the root directory
		DirItem r_f1 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 1");
		DirItem r_div12 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Divider 1.div");
		DirItem r_div13 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Divider 1.div");
		DirItem r_div14 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Divider 1.div");
		DirItem r_div15 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Divider 1.div");

		DirItem r_f2 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Airplane.jpg");
		DirItem r_f3 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Vulture.gif");
		DirItem r_f4 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Horrifying Rabbit.mp4");
		DirItem r_f5 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 5");
		DirItem r_f6 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 6");
		DirItem r_div1 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Divider 1.div");
		DirItem r_f7 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 7");
		DirItem r_d1 = new DirItem(hapi.createFile(currentAccount, true, false), false, false, "1: Child Dir");
		DirItem r_f8 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 8");
		List<DirItem> rootItems = new ArrayList<>(Arrays.asList(
				r_div12, r_div13, r_div14, r_div15, r_f1,
				r_f2,
				r_f3,
				r_f4,
				r_f5, r_div1, r_f6, r_d1, r_f7, r_f8));
		writeDirList(root, rootItems);

		//Add data to some root files
		writeUriToFile(r_f2.fileUID, externalUri_Jpg_1MB, externalUri_Jpg_1MB_Checksum);
		writeUriToFile(r_f3.fileUID, externalUri_Gif_40KB, externalUri_Gif_40KB_Checksum);
		writeUriToFile(r_f4.fileUID, externalUri_MP4_1MB, externalUri_MP4_1MB_Checksum);


		//Add tags to some root files
		writeAttrToFile(r_f2.fileUID, makeTagAttr("Actual File", "Group A"));
		writeAttrToFile(r_d1.fileUID, makeTagAttr("Directory", "Group A"));


		Log.i(TAG, "Finished setting up in-memory database!");

		return root;
	}




	public static UUID setupEmptyDatabase(Context context) throws FileNotFoundException, IOException {
		Log.i(TAG, "Setting up empty database...");
		Uri storageDir = MainStorageHandler.getStorageTreeUri(context);
		if(storageDir == null) throw new RuntimeException("Storage directory is null!");

		//LocalDatabase db = Room.inMemoryDatabaseBuilder(context, LocalDatabase.class).build();
		LocalDatabase db = new LocalDatabase.DBBuilder().newInstance(context);
		HybridAPI.initialize(db, storageDir);
		HybridAPI hapi = HybridAPI.getInstance();

		//Create the root directory for the new account
		UUID root = hapi.createFile(hapi.getCurrentAccount(), true, false);
		writeDirList(root, new ArrayList<>());

		return root;
	}


	//TODO Add an external link, a link to a single item, and a link to a file that doesn't exist
	// Also add a file with no data and a fake file for all of .jpg, .gif, .mp4


	//Returns the UUID of the root file
	public static UUID setupDatabase(Context context) throws FileNotFoundException, IOException {
		Log.i(TAG, "Setting up in-memory database...");
		Uri storageDir = MainStorageHandler.getStorageTreeUri(context);
		if(storageDir == null) throw new RuntimeException("Storage directory is null!");

		//LocalDatabase db = Room.inMemoryDatabaseBuilder(context, LocalDatabase.class).build();
		LocalDatabase db = new LocalDatabase.DBBuilder().newInstance(context);
		HybridAPI.initialize(db, storageDir);
		HybridAPI hapi = HybridAPI.getInstance();

		//Fake creating the account
		UUID currentAccount = UUID.randomUUID();
		hapi.setAccount(currentAccount);


		//Create the root directory for the new account
		UUID root = hapi.createFile(currentAccount, true, false);


		//Setup files in the root directory
		DirItem r_f1 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Airplane.jpg");
		DirItem r_f2 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Vulture.gif");
		DirItem r_f3 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Horrifying Rabbit.mp4");
		DirItem r_f4 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 4");
		DirItem r_f5 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 5");
		DirItem r_f6 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 6");
		DirItem r_f7 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 7");
		DirItem r_f8 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 8");
		DirItem r_div1 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Divider 1.div");
		DirItem r_f9 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Normal Text.txt");
		DirItem r_f10 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Richest Guy.rtf");
		DirItem r_f11 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 11");
		DirItem r_f12 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 12");
		DirItem r_f13 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 13");
		DirItem r_f14 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 14");
		DirItem r_div2 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: Divider 2.div");
		DirItem r_f15 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 15");
		DirItem r_f16 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 16");
		DirItem r_f17 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 17");
		DirItem r_f18 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "1: File 18");



		DirItem r_d1 = new DirItem(hapi.createFile(currentAccount, true, false), true, false, "1: Child Dir");

		DirItem r_l1 = new DirItem(hapi.createFile(currentAccount, false, true), false, true, "1: Link to Child dir");
		DirItem r_l2 = new DirItem(hapi.createFile(currentAccount, false, true), false, true, "1: Link to SideDir");
		DirItem r_l3 = new DirItem(hapi.createFile(currentAccount, false, true), false, true, "1: Link to Link to SideDir");
		DirItem r_l4 = new DirItem(hapi.createFile(currentAccount, false, true), false, true, "1: Link to Divider 1");
		DirItem r_l5 = new DirItem(hapi.createFile(currentAccount, false, true), false, true, "1: Link to Divider 2");

		List<DirItem> rootItems = new ArrayList<>(Arrays.asList(r_l1, r_d1,
				r_f1,
				//r_f2, r_f3,
				r_l2, r_l3, r_f4, r_f5, r_f6, r_f7, r_f8,
				r_div1, r_f9, r_f10, r_f11, r_f12, r_f13, r_f14, r_div2, r_f15, r_f16, r_f17, r_f18, r_l4, r_l5));
		writeDirList(root, rootItems);


		//Add data to some root files
		writeUriToFile(r_f1.fileUID, externalUri_Jpg_1MB, externalUri_Jpg_1MB_Checksum);
		writeUriToFile(r_f2.fileUID, externalUri_Gif_40KB, externalUri_Gif_40KB_Checksum);
		writeUriToFile(r_f3.fileUID, externalUri_MP4_1MB, externalUri_MP4_1MB_Checksum);

		writeTextToFile(r_f9.fileUID, "This is a real, genuine, bona fide, Buona Beef text file. \nRealest one you've ever seen.");
		String mystring = context.getResources().getString(R.string.lorem_ipsum_small_html);
		writeTextToFile(r_f10.fileUID, "This is a custom string straight from JC Penney. "+mystring);



		//Add tags to some root files
		writeAttrToFile(r_f1.fileUID, makeTagAttr("Actual File", "Group A"));
		writeAttrToFile(r_l1.fileUID, makeTagAttr("Link", "Group A"));
		writeAttrToFile(r_l3.fileUID, makeTagAttr("Link", "Group B"));

		JsonObject dirAttr = makeTagAttr("Directory", "Group A");
		dirAttr.addProperty("color", 0xFF9A0000);
		writeAttrToFile(r_d1.fileUID, dirAttr);

		//Add color to some files
		JsonObject colorAttr = new JsonObject();
		//colorAttr.addProperty("color", 0xFF00FF00);
		colorAttr.addProperty("color", 0xFF1CB1B6);
		writeAttrToFile(r_l2.fileUID, colorAttr);
		colorAttr.addProperty("color", 0xFFFF0000);
		writeAttrToFile(r_f7.fileUID, colorAttr);
		colorAttr.addProperty("color", 0xFF0000FF);
		writeAttrToFile(r_div2.fileUID, colorAttr);




		//Link some root files
		linkFileToFile(r_l1.fileUID, root, r_d1.fileUID);
		linkFileToFile(r_l3.fileUID, root, r_l2.fileUID);
		linkFileToFile(r_l4.fileUID, root, r_div1.fileUID);
		linkFileToFile(r_l5.fileUID, root, r_div2.fileUID);


		//-----------------------------------------------------------------------------------------


		//Setup files in a child directory
		UUID childUID = r_d1.fileUID;
		DirItem child_f1 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "2: File 1");
		DirItem child_f2 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "2: File 2");

		DirItem child_dupe_f2 = new DirItem(child_f2.fileUID, false, false, child_f2.name+" Duplicate");	//Test duplicate detection

		List<DirItem> childItems = new ArrayList<>(Arrays.asList(child_f1, child_f2, child_dupe_f2));
		writeDirList(childUID, childItems);


		//Add tags to some child dir files
		writeAttrToFile(child_f1.fileUID, makeTagAttr("Child Dir", "Group B"));
		writeAttrToFile(child_f2.fileUID, makeTagAttr("Child Dir", "Duplicate"));


		//-----------------------------------------------------------------------------------------


		//Setup files in a detached directory
		UUID detachedUID = hapi.createFile(currentAccount, true, false);
		DirItem detached_f1 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "De: File 1");
		DirItem detached_d1 = new DirItem(hapi.createFile(currentAccount, true, false), true, false, "De: Child Dir");

		List<DirItem> detachedItems = new ArrayList<>(Arrays.asList(detached_f1, detached_d1));
		writeDirList(detachedUID, detachedItems);

		Log.i(TAG, "Detached UUID: " + detachedUID);
		Log.i(TAG, "Detached Child UUID: " + detached_d1);


		//-----------------------------------------------------------------------------------------


		//Setup files in the detached child directory
		UUID detachedChildUID = detached_d1.fileUID;
		DirItem deChild_f1 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "DC: File 1");
		DirItem deChild_f2 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "DC: File 2");
		DirItem deChild_f3 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "DC: File 3");
		DirItem deChild_f4 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "DC: File 4");
		DirItem deChild_f5 = new DirItem(hapi.createFile(currentAccount, false, false), false, false, "DC: File 5");
		DirItem deChild_l1_cycle = new DirItem(hapi.createFile(currentAccount, false, true), false, true, "DC: Link Cycle");

		DirItem deChild_f6_fake = new DirItem(UUID.randomUUID(), false, false, "DC: File Fake");		//Create a file with a fake UUID

		List<DirItem> deChildItems = new ArrayList<>(Arrays.asList(deChild_f1, deChild_f2, deChild_f3, deChild_f4, deChild_f5,
				//deChild_f6_fake,
				deChild_l1_cycle));
		writeDirList(detachedChildUID, deChildItems);


		//Link a detached childDir file to the detached child directory to test link cycles
		linkFileToFile(deChild_l1_cycle.fileUID, detachedUID, detachedChildUID);

		//----------

		//Link a root link to the detached child dir
		linkFileToFile(r_l2.fileUID, detachedUID, detachedChildUID);


		//-----------------------------------------------------------------------------------------

		Log.i(TAG, "Finished setting up in-memory database!");

		return root;
	}


	private static void writeDirList(UUID dirUID, List<DirItem> dirItems) {
		HybridAPI hapi = HybridAPI.getInstance();

		List<String> dirLines = dirItems.stream().map(DirItem::toString).collect(Collectors.toList());
		byte[] newContent = String.join("\n", dirLines).getBytes();
		try {
			hapi.lockLocal(dirUID);
			hapi.writeFile(dirUID, newContent, HFile.defaultChecksum);
		}
		catch (FileNotFoundException | ConnectException e) { throw new RuntimeException(e); }
		catch (IOException e) { throw new RuntimeException(e); }
		finally { hapi.unlockLocal(dirUID); }
	}


	private static void writeUriToFile(UUID fileUID, Uri uri, String checksum) {
		HybridAPI hapi = HybridAPI.getInstance();

		try {
			hapi.lockLocal(fileUID);
			hapi.writeFile(fileUID, uri, checksum, HFile.defaultChecksum);
		}
		catch (FileNotFoundException | ConnectException e) { throw new RuntimeException(e); }
		catch (IOException e) { /*Skip, can't connect*/ }
		finally { hapi.unlockLocal(fileUID); }
	}


	private static void writeTextToFile(UUID fileUID, String content) {
		HybridAPI hapi = HybridAPI.getInstance();

		try {
			hapi.lockLocal(fileUID);
			hapi.writeFile(fileUID, content.getBytes(), HFile.defaultChecksum);
		}
		catch (FileNotFoundException | ConnectException e) { throw new RuntimeException(e); }
		catch (IOException e) { /*Skip, can't connect*/ }
		finally { hapi.unlockLocal(fileUID); }
	}



	private static void writeAttrToFile(UUID fileUID, JsonObject attr) {
		HybridAPI hapi = HybridAPI.getInstance();

		try {
			hapi.lockLocal(fileUID);
			hapi.setAttributes(fileUID, attr, HFile.defaultAttrHash);
		}
		catch (FileNotFoundException | ConnectException e) { throw new RuntimeException(e); }
		finally { hapi.unlockLocal(fileUID); }
	}

	private static JsonObject makeTagAttr(String... tags) {
		JsonObject attr = new JsonObject();
		JsonArray tagArray = new JsonArray();
		for (String tag : tags) {
			tagArray.add(tag);
		}
		attr.add("tags", tagArray);
		return attr;
	}


	private static void linkFileToFile(UUID fileUID, UUID targetParentUID, UUID targetUID) {
		HybridAPI hapi = HybridAPI.getInstance();

		try {
			hapi.lockLocal(fileUID);
			InternalTarget targetInternal = new InternalTarget(targetUID, targetParentUID);
			hapi.writeFile(fileUID, targetInternal.toString().getBytes(), HFile.defaultChecksum);
		}
		catch (FileNotFoundException | ConnectException e) { throw new RuntimeException(e); }
		catch (IOException e) { throw new RuntimeException(e); }
		finally { hapi.unlockLocal(fileUID); }
	}
	private static void linkFileToUri(UUID fileUID, Uri uri) {
		HybridAPI hapi = HybridAPI.getInstance();

		try {
			hapi.lockLocal(fileUID);
			ExternalTarget targetExternal = new ExternalTarget(uri);
			hapi.writeFile(fileUID, targetExternal.toString().getBytes(), HFile.defaultChecksum);
		}
		catch (FileNotFoundException | ConnectException e) { throw new RuntimeException(e); }
		catch (IOException e) { throw new RuntimeException(e); }
		finally { hapi.unlockLocal(fileUID); }
	}



	public static void fakeImportFiles(UUID dirUID, int numFiles) {
		HybridAPI hAPI = HybridAPI.getInstance();
		try {
			//Grab the current contents
			System.out.println("Locking for fake import");
			hAPI.lockLocal(dirUID);
			List<DirItem> dirList = DirUtilities.readDir(dirUID);
			String checksum = hAPI.getFileProps(dirUID).checksum;


			//Add a number of random fake files
			for(int i = 0; i < numFiles; i++) {
				dirList.add(0, new DirItem(UUID.randomUUID(), false, false, "New Media "+ (dirList.size()+i) +".jpg"));
			}

			//Write the list back to the directory
			List<String> newLines = dirList.stream().map(DirItem::toString)
					.collect(Collectors.toList());
			byte[] newContent = String.join("\n", newLines).getBytes();
			hAPI.writeFile(dirUID, newContent, checksum);
		}
		catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			throw new RuntimeException(e);
		}
		catch (IOException e) { throw new RuntimeException(e); }
		finally {
			System.out.println("Unlocking for fake import");
			hAPI.unlockLocal(dirUID);
		}
	}
}
