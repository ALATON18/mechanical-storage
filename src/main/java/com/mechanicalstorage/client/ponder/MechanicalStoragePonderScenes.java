package com.mechanicalstorage.client.ponder;

import com.mechanicalstorage.MechanicalStorage;
import com.mechanicalstorage.block.DirectionalMachineBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class MechanicalStoragePonderScenes {
	private MechanicalStoragePonderScenes() {
	}

	public static void storageNetwork(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		scene.title("storage_network", "Connecting a Mechanical Storage Network");
		scene.configureBasePlate(0, 0, 7);
		scene.scaleSceneView(0.85F);

		BlockPos chest = util.grid().at(1, 1, 2);
		BlockPos connector = util.grid().at(1, 1, 3);
		BlockPos tank = util.grid().at(5, 1, 2);
		BlockPos cogwheelConnector = util.grid().at(5, 1, 3);
		BlockPos cogwheel = util.grid().at(4, 1, 3);
		BlockPos terminal = util.grid().at(3, 1, 4);
		BlockPos speedIndicator = util.grid().at(2, 1, 5);

		Selection storage = util.select().position(chest)
			.add(util.select().position(tank));
		Selection cogwheelConnectorSelection = util.select().position(cogwheelConnector);
		Selection connectors = util.select().position(connector)
			.add(cogwheelConnectorSelection);
		Selection drive = util.select().fromTo(0, 1, 5, 4, 1, 5)
			.add(util.select().position(1, 1, 4))
			.add(util.select().position(4, 1, 4))
			.add(util.select().position(cogwheel));
		Selection terminalSelection = util.select().position(terminal);
		Selection kineticNetwork = drive.copy()
			.add(connectors)
			.add(terminalSelection);

		scene.showBasePlate();
		scene.idle(10);

		scene.world().showSection(storage, Direction.DOWN);
		scene.idle(10);
		scene.overlay().showText(55)
			.text("Connect item inventories and fluid tanks to access their contents from a Terminal.")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().centerOf(chest));
		scene.overlay().showControls(util.vector().topOf(chest), Pointing.DOWN, 45)
			.withItem(new ItemStack(Items.DIAMOND));
		scene.overlay().showControls(util.vector().topOf(tank), Pointing.DOWN, 45)
			.withItem(new ItemStack(Items.WATER_BUCKET));
		scene.idle(65);

		scene.world().showSection(connectors, Direction.SOUTH);
		scene.idle(10);
		scene.overlay().showOutline(PonderPalette.GREEN, "item_target", util.select().fromTo(chest, connector), 75);
		scene.overlay().showOutline(PonderPalette.GREEN, "fluid_target", util.select().fromTo(tank, cogwheelConnector), 75);
		scene.overlay().showLine(PonderPalette.GREEN,
				util.vector().centerOf(connector), util.vector().centerOf(chest), 75);
		scene.overlay().showLine(PonderPalette.GREEN,
				util.vector().centerOf(cogwheelConnector), util.vector().centerOf(tank), 75);
		scene.overlay().showText(75)
			.text("Point a Connector's front face directly at one adjacent inventory or fluid tank.")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(connector, Direction.NORTH));
		scene.idle(85);

		scene.world().showSection(drive, Direction.DOWN);
		scene.idle(12);
		scene.world().showSection(terminalSelection, Direction.SOUTH);
		scene.idle(12);
		scene.world().setKineticSpeed(kineticNetwork, 16);
		scene.world().setKineticSpeed(cogwheelConnectorSelection, -16);
		scene.effects().rotationSpeedIndicator(speedIndicator);
		scene.overlay().showText(80)
			.text("Power every Terminal and Connector from the same rotating Create kinetic network.")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().centerOf(speedIndicator));
		scene.idle(90);

		scene.overlay().showOutline(PonderPalette.BLUE, "cogwheel_input",
				util.select().position(cogwheel).add(util.select().position(cogwheelConnector)), 70);
		scene.overlay().showText(70)
			.text("Mesh a Cogwheel Connector with an adjacent cog to connect storage without a rear shaft.")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().centerOf(cogwheelConnector));
		scene.idle(80);

		scene.overlay().showText(70)
			.text("Networks require at least 2 RPM and stop working when overstressed.")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().centerOf(terminal));
		scene.idle(80);

		scene.overlay().showControls(util.vector().blockSurface(terminal, Direction.NORTH), Pointing.RIGHT, 60)
			.rightClick();
		scene.overlay().showText(70)
			.text("Right-click a Terminal to browse and transfer connected items and fluids.")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(terminal, Direction.NORTH));
		scene.idle(80);

		scene.world().hideSection(terminalSelection, Direction.DOWN);
		scene.idle(15);
		scene.world().setBlock(terminal, Blocks.AIR.defaultBlockState(), false);
		scene.overlay().showControls(util.vector().topOf(terminal), Pointing.DOWN, 40)
			.withItem(MechanicalStorage.CRAFTING_TERMINAL.asStack());
		scene.idle(10);
		scene.world().setBlock(terminal, MechanicalStorage.CRAFTING_TERMINAL.getDefaultState()
			.setValue(DirectionalMachineBlock.FACING, Direction.NORTH), false);
		scene.world().showSection(terminalSelection, Direction.UP);
		scene.effects().indicateSuccess(terminal);
		scene.idle(20);
		scene.overlay().showText(70)
			.text("Place a Crafting Terminal for storage access with a private 3x3 crafting grid.")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(terminal, Direction.NORTH));
		scene.idle(80);
		scene.markAsFinished();
	}
}
