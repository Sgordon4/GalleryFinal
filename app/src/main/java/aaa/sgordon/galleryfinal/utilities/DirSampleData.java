package aaa.sgordon.galleryfinal.utilities;

import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.room.Room;

import com.google.gson.JsonArray;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import aaa.sgordon.galleryfinal.repository.hybrid.ContentsNotFoundException;
import aaa.sgordon.galleryfinal.repository.hybrid.HybridAPI;
import aaa.sgordon.galleryfinal.repository.hybrid.types.HFile;
import aaa.sgordon.galleryfinal.repository.local.LocalRepo;
import aaa.sgordon.galleryfinal.repository.local.database.LocalDatabase;

public class DirSampleData {

	private static final Uri externalUri_Jpg_1MB = Uri.parse("https://sample-videos.com/img/Sample-jpg-image-1mb.jpg");
	private static final String externalUri_Jpg_1MB_Checksum = "35C461DEE98AAD4739707C6CCA5D251A1617BFD928E154995CA6F4CE8156CFFC";

	private static final Uri externalUri_Gif_40KB = Uri.parse("https://sample-videos.com/gif/3.gif");
	private static final String externalUri_Gif_40KB_Checksum= "0FF064BA36E4F493F6A1B3D9D29C8EEE1B719E39FC6768C5A6129534869C380B";

	private static final Uri externalUri_MP4_1MB = Uri.parse("https://sample-videos.com/video321/mp4/720/big_buck_bunny_720p_1mb.mp4");
	private static final String externalUri_MP4_1MB_Checksum= "F25B31F155970C46300934BDA4A76CD2F581ACAB45C49762832FFDFDDBCF9FDD";



	public static UUID setupDatabase_Small(Context context) throws FileNotFoundException {
		LocalDatabase db = Room.inMemoryDatabaseBuilder(context, LocalDatabase.class).build();
		LocalRepo.initialize(db, context.getCacheDir().toString());
		HybridAPI hapi = HybridAPI.getInstance();

		//Fake creating the account
		UUID currentAccount = hapi.getCurrentAccount();
		hapi.setAccount(currentAccount);

		//Create the root directory for the new account
		UUID root = hapi.createFile(currentAccount, true, false);

		//Setup files in the root directory
		Pair<UUID, String> r_l1 = new Pair<>(hapi.createFile(currentAccount, true, true), "Root link to dir 1");
		Pair<UUID, String> r_f1 = new Pair<>(hapi.createFile(currentAccount, false, false), "Root file 1");
		Pair<UUID, String> r_f2 = new Pair<>(hapi.createFile(currentAccount, false, false), "Root file 2");
		Pair<UUID, String> r_f3 = new Pair<>(hapi.createFile(currentAccount, false, false), "Root file 3");
		List<Pair<UUID, String>> rootList = new ArrayList<>(Arrays.asList(r_l1, r_f1, r_f2, r_f3));

		//Write the list to the root directory
		List<String> rootLines = rootList.stream().map(pair -> pair.first+" "+pair.second)
				.collect(Collectors.toList());
		byte[] newContent = String.join("\n", rootLines).getBytes();
		try {
			hapi.lockLocal(root);
			hapi.writeFile(root, newContent, HFile.defaultChecksum);
		} finally { hapi.unlockLocal(root); }


		//Setup files in Root Dir 1
		Pair<UUID, String> r_d1 = new Pair<>(hapi.createFile(currentAccount, true, false), "Root dir 1");
		List<Pair<UUID, String>> r_d1_List = new ArrayList<>(Collections.emptyList());

		//Write the list to dir 1
		List<String> dir1Lines = r_d1_List.stream().map(pair -> pair.first+" "+pair.second)
				.collect(Collectors.toList());
		newContent = String.join("\n", dir1Lines).getBytes();
		try {
			hapi.lockLocal(r_d1.first);
			hapi.writeFile(r_d1.first, newContent, HFile.defaultChecksum);
		} finally { hapi.unlockLocal(r_d1.first); }


		//Link r_l1 to r_d1
		try {
			hapi.lockLocal(r_l1.first);
			hapi.writeFile(r_l1.first, r_d1.first.toString().getBytes(), HFile.defaultChecksum);
		} finally { hapi.unlockLocal(r_l1.first); }

		return root;
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
		Pair<UUID, String> r_f1 = new Pair<>(hapi.createFile(currentAccount, false, false), "Root file 1.jpg");
		Pair<UUID, String> r_f2 = new Pair<>(hapi.createFile(currentAccount, false, false), "Root file 2.gif");
		Pair<UUID, String> r_f3 = new Pair<>(hapi.createFile(currentAccount, false, false), "Root file 3.mp4");
		Pair<UUID, String> r_l2 = new Pair<>(hapi.createFile(currentAccount, true, true), "Root link to link to sideDir");
		Pair<UUID, String> r_l3 = new Pair<>(hapi.createFile(currentAccount, true, true), "Root link to sideDir");
		Pair<UUID, String> r_f4 = new Pair<>(hapi.createFile(currentAccount, false, false), "Root file 4");
		Pair<UUID, String> r_f5 = new Pair<>(hapi.createFile(currentAccount, false, false), "Root file 5");
		List<Pair<UUID, String>> rootList = new ArrayList<>(Arrays.asList(r_l1, r_d1, r_f1, r_f2, r_f3, r_l2, r_l3, r_f4, r_f5));

		Pair<UUID, String> sideDir = new Pair<>(hapi.createFile(currentAccount, true, false), "Side dir");



		/*
		//Use to get the checksum of a new external file
		try {
			Path tempFile = Paths.get(MyApplication.getAppContext().getDataDir().toString(), "temp", "smallFile.txt");
			URL url = new URL(externalUri_MP4_1MB.toString());
			System.out.println("Checksum: "+importToTempFile(tempFile, url));
			assert false;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		/**/

		//Import an actual image to one of the files
		Thread goFuckYourself = new Thread(() -> {
			try {
				UUID fileUID = r_f1.first;
				hapi.lockLocal(fileUID);
				HFile fileProps = hapi.getFileProps(fileUID);
				hapi.writeFile(fileUID, externalUri_Jpg_1MB, externalUri_Jpg_1MB_Checksum, fileProps.checksum);
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				hapi.unlockLocal(r_f1.first);
			}

			try {
				UUID fileUID = r_f2.first;
				hapi.lockLocal(fileUID);
				HFile fileProps = hapi.getFileProps(fileUID);
				hapi.writeFile(fileUID, externalUri_Gif_40KB, externalUri_Gif_40KB_Checksum, fileProps.checksum);
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				hapi.unlockLocal(r_f2.first);
			}

			try {
				UUID fileUID = r_f3.first;
				hapi.lockLocal(fileUID);
				HFile fileProps = hapi.getFileProps(fileUID);
				hapi.writeFile(fileUID, externalUri_MP4_1MB, externalUri_MP4_1MB_Checksum, fileProps.checksum);
			} catch (IOException e) {
				throw new RuntimeException(e);
			} finally {
				hapi.unlockLocal(r_f3.first);
			}
		});
		goFuckYourself.start();



		Thread attributeThread = new Thread(() -> {
			try {
				UUID fileUID = r_l1.first;
				hapi.lockLocal(fileUID);
				HFile fileProps = hapi.getFileProps(fileUID);
				JsonArray tags = new JsonArray();
				tags.add("link");
				tags.add("combined");
				fileProps.userattr.add("tags", tags);
				hapi.setAttributes(fileUID, fileProps.userattr, fileProps.attrhash);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			} finally {
				hapi.unlockLocal(r_l1.first);
			}

			try {
				UUID fileUID = r_d1.first;
				hapi.lockLocal(fileUID);
				HFile fileProps = hapi.getFileProps(fileUID);
				JsonArray tags = new JsonArray();
				tags.add("directory");
				tags.add("combined");
				fileProps.userattr.add("tags", tags);
				hapi.setAttributes(fileUID, fileProps.userattr, fileProps.attrhash);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			} finally {
				hapi.unlockLocal(r_d1.first);
			}

			try {
				UUID fileUID = r_l3.first;
				hapi.lockLocal(fileUID);
				HFile fileProps = hapi.getFileProps(fileUID);
				JsonArray tags = new JsonArray();
				tags.add("link");
				tags.add("notcombined");
				fileProps.userattr.add("tags", tags);
				hapi.setAttributes(fileUID, fileProps.userattr, fileProps.attrhash);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			} finally {
				hapi.unlockLocal(r_l3.first);
			}

			try {
				UUID fileUID = r_f1.first;
				hapi.lockLocal(fileUID);
				HFile fileProps = hapi.getFileProps(fileUID);
				JsonArray tags = new JsonArray();
				tags.add("actualfile");
				tags.add("combined");
				fileProps.userattr.add("tags", tags);
				hapi.setAttributes(fileUID, fileProps.userattr, fileProps.attrhash);
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			} finally {
				hapi.unlockLocal(r_f1.first);
			}
		});
		attributeThread.start();


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
			Uri target = new Uri.Builder().scheme("gallery").appendPath(root.toString()).appendPath(r_d1.first.toString()).build();
			hapi.writeFile(r_l1.first, target.toString().getBytes(), HFile.defaultChecksum);
		} finally { hapi.unlockLocal(r_l1.first); }

		//Link r_l2 to r_l3
		try {
			hapi.lockLocal(r_l2.first);
			Uri target = new Uri.Builder().scheme("gallery").appendPath(root.toString()).appendPath(r_l3.first.toString()).build();
			hapi.writeFile(r_l2.first, target.toString().getBytes(), HFile.defaultChecksum);
		} finally { hapi.unlockLocal(r_l2.first); }

		//Link r_l3 to sideDir
		try {
			hapi.lockLocal(r_l3.first);
			Uri target = new Uri.Builder().scheme("gallery").appendPath(root.toString()).appendPath(sideDir.first.toString()).build();
			hapi.writeFile(r_l3.first, target.toString().getBytes(), HFile.defaultChecksum);
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

		//Setup files in Root Dir 1
		Pair<UUID, String> r_d1_f1 = new Pair<>(hapi.createFile(currentAccount, false, false), "D1 file 1");
		Pair<UUID, String> r_d1_f2 = new Pair<>(hapi.createFile(currentAccount, false, false), "D1 file 2");
		Pair<UUID, String> r_d1_f3 = new Pair<>(hapi.createFile(currentAccount, false, false), "D1 file 3");
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
		Pair<UUID, String> r_d2_f1 = new Pair<>(hapi.createFile(currentAccount, false, false), "SideDir file 1");
		Pair<UUID, String> r_d2_f2 = new Pair<>(hapi.createFile(currentAccount, false, false), "SideDir file 2");
		Pair<UUID, String> r_d2_f3 = new Pair<>(hapi.createFile(currentAccount, false, false), "SideDir file 3");
		Pair<UUID, String> r_d2_f4 = new Pair<>(hapi.createFile(currentAccount, false, false), "SideDir file 4");
		Pair<UUID, String> r_d2_f5 = new Pair<>(hapi.createFile(currentAccount, false, false), "SideDir file 5");
		List<Pair<UUID, String>> sideDir_List = new ArrayList<>(Arrays.asList(r_d2_f1, r_d2_f2, r_d2_f3, r_d2_f4, r_d2_f5));

		Pair<UUID, String> r_l3_again = new Pair<>(r_l3.first, "SideDir "+r_l3.second+" again");
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



	public static void fakeImportFiles(@NonNull UUID destinationDirUID, int numImported) {
		HybridAPI hAPI = HybridAPI.getInstance();

		try {
			hAPI.lockLocal(destinationDirUID);

			Pair<Uri, String> dirContent = hAPI.getFileContent(destinationDirUID);
			Uri dirUri = dirContent.first;
			String dirChecksum = dirContent.second;


			//Read the directory into a list of UUID::FileName pairs
			ArrayList<Pair<UUID, String>> dirList = new ArrayList<>();
			try (InputStream inputStream = new URL(dirUri.toString()).openStream();
				 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

				String line;
				while ((line = reader.readLine()) != null) {
					//Split each line into UUID::FileName and add it to our list
					String[] parts = line.trim().split(" ", 2);
					Pair<UUID, String> entry = new Pair<>(UUID.fromString(parts[0]), parts[1]);
					dirList.add(entry);
				}
			}
			catch (IOException e) { throw new RuntimeException(e); }


			//Add our new 'imported' files to the beginning
			for(int i = 0; i < numImported; i++) {
				UUID fileUID = UUID.randomUUID();
				String fileName = "File number "+dirList.size();
				dirList.add(0, new Pair<>(fileUID, fileName));
			}


			//Write the list back to the directory
			List<String> newLines = dirList.stream().map(pair -> pair.first+" "+pair.second)
					.collect(Collectors.toList());
			byte[] newContent = String.join("\n", newLines).getBytes();
			hAPI.writeFile(destinationDirUID, newContent, dirChecksum);

		} catch (ContentsNotFoundException | FileNotFoundException | ConnectException e) {
			throw new RuntimeException(e);
		} finally {
			hAPI.unlockLocal(destinationDirUID);
		}
	}


	//Returns filehash
	private static String importToTempFile(Path tempFile, URL url) throws IOException {
		if(!tempFile.toFile().exists()) {
			Files.createDirectories(tempFile.getParent());
			Files.createFile(tempFile);
		}

		try (BufferedInputStream in = new BufferedInputStream(url.openStream());
			 DigestInputStream dis = new DigestInputStream(in, MessageDigest.getInstance("SHA-256"));
			 FileOutputStream fileOutputStream = new FileOutputStream(tempFile.toFile())) {

			byte[] dataBuffer = new byte[1024];
			int bytesRead;
			while ((bytesRead = dis.read(dataBuffer, 0, 1024)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}

			return Utilities.bytesToHex( dis.getMessageDigest().digest() );
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}
}
