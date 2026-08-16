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
		Selection connectors = util.select().position(connector)
			.add(util.select().position(cogwheelConnector));
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
			.text("Mechanical Storage can expose both item inventories and fluid tanks")
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
			.text("A Connector reads exactly one adjacent storage block on its front face")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(connector, Direction.NORTH));
		scene.idle(85);

		scene.world().showSection(drive, Direction.DOWN);
		scene.idle(12);
		scene.world().showSection(terminalSelection, Direction.SOUTH);
		scene.idle(12);
		scene.world().setKineticSpeed(kineticNetwork, 16);
		scene.effects().rotationSpeedIndicator(speedIndicator);
		scene.overlay().showText(80)
			.text("Shafts and cogwheels carry the storage network; the Terminal and all Connectors must share the same rotating kinetic network")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().centerOf(speedIndicator));
		scene.idle(90);

		scene.overlay().showOutline(PonderPalette.BLUE, "cogwheel_input",
				util.select().position(cogwheel).add(util.select().position(cogwheelConnector)), 70);
		scene.overlay().showText(70)
			.text("The Cogwheel Connector meshes with an adjacent cog instead of accepting a shaft at its rear")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().centerOf(cogwheelConnector));
		scene.idle(80);

		scene.overlay().showText(70)
			.text("The network needs at least 2 RPM and goes offline when stopped or overstressed")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().centerOf(terminal));
		scene.idle(80);

		scene.overlay().showControls(util.vector().blockSurface(terminal, Direction.NORTH), Pointing.RIGHT, 60)
			.rightClick();
		scene.overlay().showText(70)
			.text("Right-click a Terminal to browse and transfer all connected items and fluids")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(terminal, Direction.NORTH));
		scene.idle(80);

		scene.world().setBlock(terminal, MechanicalStorage.CRAFTING_TERMINAL.getDefaultState()
			.setValue(DirectionalMachineBlock.FACING, Direction.NORTH), true);
		scene.effects().indicateSuccess(terminal);
		scene.idle(12);
		scene.overlay().showControls(util.vector().topOf(terminal), Pointing.DOWN, 60)
			.withItem(MechanicalStorage.CRAFTING_TERMINAL.asStack());
		scene.overlay().showText(70)
			.text("A Crafting Terminal adds a private 3x3 crafting grid while keeping normal terminal access")
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(terminal, Direction.NORTH));
		scene.idle(80);
		scene.markAsFinished();
	}
}
