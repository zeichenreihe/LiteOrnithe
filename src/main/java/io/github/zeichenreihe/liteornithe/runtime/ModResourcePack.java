package io.github.zeichenreihe.liteornithe.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.TextureUtil;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.resource.metadata.ResourceMetadataSection;
import net.minecraft.client.resource.metadata.ResourceMetadataSerializerRegistry;
import net.minecraft.client.resource.pack.ResourcePack;
import net.minecraft.client.resource.pack.ZippedResourcePack;
import net.minecraft.resource.Identifier;

import net.fabricmc.loader.api.metadata.ModOrigin;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModResourcePack extends ZippedResourcePack implements ResourcePack {
	private final ModContainer container;
	private final Path root;

	@Nullable
	public static ModResourcePack create(String modId, ModContainer container) {
		ModOrigin origin = container.getOrigin();
		if (origin.getKind().equals(ModOrigin.Kind.UNKNOWN)) {
			return null;
		} else {
			File file = container.getOrigin().getPaths().get(0).toFile();
			return new ModResourcePack(modId, container, file);
		}
	}

	private ModResourcePack(String modId, ModContainer container, File file) {
		super(file);
		this.container = container;
		this.root = container.getRootPaths().get(0);

		/*
		VoxelMap depends on itself being a ZipResourcePack with a non-null ZipFile, it also expects us to be
		a ZipResourcePack

		The crash that's caused by not doing this:
		java.lang.RuntimeException: java.lang.ArithmeticException: / by zero
			at de.skyrising.litefabric.runtime.LiteFabric.onInitCompleted(LiteFabric.java:111)
			at net.minecraft.client.MinecraftClient.handler$zzn000$litefabric$onGameInitDone(MinecraftClient.java:4548)
			at net.minecraft.client.MinecraftClient.initializeGame(MinecraftClient.java:515)
			at net.minecraft.client.MinecraftClient.run(MinecraftClient.java:361)
			at net.minecraft.client.main.Main.main(Main.java:109)
			at net.fabricmc.loader.impl.game.minecraft.MinecraftGameProvider.launch(MinecraftGameProvider.java:461)
			at net.fabricmc.loader.impl.launch.knot.Knot.launch(Knot.java:74)
			at net.fabricmc.loader.launch.knot.KnotClient.main(KnotClient.java:28)
		Caused by: java.lang.ArithmeticException: / by zero
			at net.minecraft.client.texture.TextureUtil.method_7022(TextureUtil.java:149)
			at net.minecraft.client.texture.TextureUtil.method_5861(TextureUtil.java:48) // third parameter of this function is 0
			at com.mamiyaotaru.voxelmap.c.h.if(Unknown Source)
			at com.mamiyaotaru.voxelmap.u.do(Unknown Source)
			at com.mamiyaotaru.voxelmap.t.reload(Unknown Source)
			at net.minecraft.resource.ReloadableResourceManagerImpl.registerListener(ReloadableResourceManagerImpl.java:99)
			at com.mamiyaotaru.voxelmap.t.do(Unknown Source)
			at com.mamiyaotaru.voxelmap.litemod.LiteModVoxelMap.onInitCompleted(Unknown Source)
			at de.skyrising.litefabric.runtime.LiteFabric.onInitCompleted(LiteFabric.java:109)
			... 7 more
		(note: this crash uses legacy-fabric yarn mappings)

		Calling this here makes the ZipFile no longer null, fixing this crash.
		 */
		if ("voxelmap".equals(modId))
			hasResource("");
	}

	@Override
	public InputStream getResource(Identifier id) throws IOException {
		return Files.newInputStream(getPath(id));
	}

	@Override
	public boolean hasResource(Identifier id) {
		return Files.exists(getPath(id));
	}

	@Override
	public Set<String> getNamespaces() {
		try (Stream<Path> stream = Files.list(getPath("assets"))) {
			return stream.filter(Files::isDirectory)
					.map(Path::getFileName)
					.map(Path::toString)
					.map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
					.collect(Collectors.toSet());
		} catch (IOException e) {
			return Collections.emptySet();
		}
	}

	@Nullable
	@Override
	public <T extends ResourceMetadataSection> T getMetadataSection(
			ResourceMetadataSerializerRegistry serializer,
			String key
	) throws IOException {
		InputStream packMcmeta;
		try {
			packMcmeta = this.openFile0("pack.mcmeta");
		} catch (NoSuchFileException ignored) {
			return null;
		}

		BufferedReader bufferedReader = null;
		try {
			bufferedReader = new BufferedReader(new InputStreamReader(packMcmeta, StandardCharsets.UTF_8));
			JsonObject jsonObject = new JsonParser().parse(bufferedReader).getAsJsonObject();
			return serializer.readMetadata(key, jsonObject);
		} finally {
			IOUtils.closeQuietly(bufferedReader);
		}

	}

	@Override
	public BufferedImage getIcon() throws IOException {
		return TextureUtil.readImage(this.openFile0("pack.png"));
	}

	@Override
	public String getName() {
		return this.container.getMetadata().getName();
	}

	private InputStream openFile0(String file) throws IOException {
		return Files.newInputStream(getPath(file));
	}

	private Path getPath(String file) {
		return this.root.resolve(file);
	}

	private Path getPath(Identifier id) {
		return this.root.resolve("assets").resolve(id.getNamespace()).resolve(id.getPath());
	}
}
