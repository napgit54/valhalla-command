package com.valhalla.command.entity;

import net.minecraft.util.RandomSource;

public final class VikingNames {
	private static final String[] MALE = {
			"Bjorn", "Ragnar", "Leif", "Erik", "Harald", "Sigurd", "Ivar", "Ulf", "Gunnar", "Thorvald",
			"Olaf", "Halfdan", "Arne", "Knut", "Sven", "Torstein", "Rolf", "Egil", "Hakon", "Ketil",
			"Asgeir", "Vidar", "Hrolf", "Ingvar", "Orm", "Snorri", "Floki", "Hallbjorn", "Eirik", "Thorkel"
	};
	private static final String[] FEMALE = {
			"Astrid", "Freydis", "Ingrid", "Sigrid", "Gudrun", "Helga", "Lagertha", "Thyra", "Ragnhild", "Solveig",
			"Brynhild", "Gunnhild", "Hilda", "Estrid", "Ylva", "Runa", "Sif", "Torunn", "Aslaug", "Liv",
			"Dagny", "Eira", "Frida", "Gyda", "Hervor", "Inga", "Jorunn", "Ragna", "Svanhild", "Valdis"
	};

	private VikingNames() {}

	public static String random(boolean female, RandomSource random) {
		String[] list = female ? FEMALE : MALE;
		return list[random.nextInt(list.length)];
	}
}
