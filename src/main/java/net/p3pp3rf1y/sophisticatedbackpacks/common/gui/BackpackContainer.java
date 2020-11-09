package net.p3pp3rf1y.sophisticatedbackpacks.common.gui;

import it.unimi.dsi.fastutil.ints.IntComparators;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.registries.ObjectHolder;
import net.p3pp3rf1y.sophisticatedbackpacks.SophisticatedBackpacks;
import net.p3pp3rf1y.sophisticatedbackpacks.blocks.tile.BackpackTileEntity;
import net.p3pp3rf1y.sophisticatedbackpacks.items.ScreenProperties;
import net.p3pp3rf1y.sophisticatedbackpacks.util.BackpackInventoryEventBus;
import net.p3pp3rf1y.sophisticatedbackpacks.util.BackpackInventoryHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.util.BackpackUpgradeHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.util.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.util.InventoryHelper;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryHandler;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;
import net.p3pp3rf1y.sophisticatedbackpacks.util.WorldHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class BackpackContainer extends Container {
	@ObjectHolder(SophisticatedBackpacks.MOD_ID + ":backpack")
	public static ContainerType<BackpackContainer> TYPE;

	@ObjectHolder(SophisticatedBackpacks.MOD_ID + ":backpack_block")
	public static ContainerType<BackpackContainer> BLOCK_TYPE;

	private static final int NUMBER_OF_PLAYER_SLOTS = 36;

	private final BackpackWrapper backPackWrapper;
	private int backpackSlotNumber = -1;

	private final Map<ResourceLocation, UpgradeContainerBase> upgradeContainers = new HashMap<>(); //TODO perhaps remove ResourceLocation from here
	private Consumer<BackpackContainer> upgradeChangeListener = null;

	public BackpackContainer(int windowId, PlayerEntity player, String handlerName, int backpackSlot) {
		super(TYPE, windowId);
		Optional<PlayerInventoryHandler> h = PlayerInventoryProvider.getPlayerInventoryHandler(handlerName);

		if (!h.isPresent()) {
			backPackWrapper = BackpackWrapper.getEmpty();
			return;
		}
		PlayerInventoryHandler handler = h.get();
		backPackWrapper = new BackpackWrapper(handler.getStackInSlot(player, backpackSlot).copy());
		if (!player.world.isRemote) {
			backPackWrapper.setPersistent(player, handlerName, backpackSlot, false); //don't notify because notification is only consumed by open gui and we're in it (it would also cause an infinite loop)
			BackpackInventoryEventBus.registerListener(player.getUniqueID(), (hName, backpackInSlot, slot, newStack) -> {
				if (hName.equals(handlerName) && backpackInSlot == backpackSlot) {
					backPackWrapper.onInventorySlotUpdate(slot, newStack);
				}
			});
		}
		int yPosition = addBackpackInventorySlots();
		addBackpackUpgradeSlots(yPosition);
		addPlayerInventorySlots(player.inventory, yPosition, backpackSlot, handler.isVisibleInGui());
		addUpgradeSettingsContainers();
	}

	public BackpackContainer(int windowId, PlayerEntity player, BlockPos pos) {
		super(BLOCK_TYPE, windowId);
		backPackWrapper = WorldHelper.getTile(player.world, pos, BackpackTileEntity.class).map(te -> te.getBackpackWrapper().orElse(BackpackWrapper.getEmpty())).orElse(BackpackWrapper.getEmpty());
		int yPosition = addBackpackInventorySlots();
		addBackpackUpgradeSlots(yPosition);
		addPlayerInventorySlots(player.inventory, yPosition, -1, false);
		addUpgradeSettingsContainers();
	}

	private void removeUpgradeSettingsSlots() {
		List<Integer> slotNumbersToRemove = new ArrayList<>();
		for (UpgradeContainerBase container : upgradeContainers.values()) {
			container.getSlots().forEach(slot -> {
				slotNumbersToRemove.add(slot.slotNumber);
				inventorySlots.remove(slot);
			});
		}
		slotNumbersToRemove.sort(IntComparators.OPPOSITE_COMPARATOR);
		for (int slotNumber : slotNumbersToRemove) {
			inventoryItemStacks.remove(slotNumber);
		}
	}

	private void addUpgradeSettingsContainers() {
		InventoryHelper.iterate(backPackWrapper.getUpgradeHandler(), (slot, stack) -> {
			ResourceLocation registryName = stack.getItem().getRegistryName();
			UpgradeContainerRegistry.instantiateContainer(stack).ifPresent(container -> upgradeContainers.put(registryName, container));
		});

		for (UpgradeContainerBase container : upgradeContainers.values()) {
			container.getSlots().forEach(this::addSlot);
		}
	}

	private void addBackpackUpgradeSlots(int lastInventoryRowY) {
		BackpackUpgradeHandler upgradeHandler = backPackWrapper.getUpgradeHandler();

		int numberOfSlots = upgradeHandler.getSlots();

		if (numberOfSlots == 0) {
			return;
		}

		int slotIndex = 0;

		int yPosition = lastInventoryRowY - (22 + 22 * (numberOfSlots - 1));

		while (slotIndex < upgradeHandler.getSlots()) {
			addSlot(new SlotItemHandler(upgradeHandler, slotIndex, -18, yPosition) {
				@Override
				public void onSlotChanged() {
					super.onSlotChanged();

					removeUpgradeSettingsSlots();
					upgradeContainers.clear();
					addUpgradeSettingsContainers();
					onUpgradesChanged();
				}
			});

			slotIndex++;
			yPosition += 22;
		}
	}

	public void setUpgradeChangeListener(Consumer<BackpackContainer> upgradeChangeListener) {
		this.upgradeChangeListener = upgradeChangeListener;
	}

	private void onUpgradesChanged() {
		if (upgradeChangeListener != null) {
			upgradeChangeListener.accept(this);
		}
	}

	private int addBackpackInventorySlots() {
		BackpackInventoryHandler inventoryHandler = backPackWrapper.getInventoryHandler();
		int slotIndex = 0;
		int yPosition = 18;

		while (slotIndex < inventoryHandler.getSlots()) {
			int lineIndex = slotIndex % getSlotsOnLine();
			int finalSlotIndex = slotIndex;
			addSlot(new SlotItemHandler(inventoryHandler, finalSlotIndex, 8 + lineIndex * 18, yPosition) {
				@Override
				public void onSlotChanged() {
					super.onSlotChanged();
					// saving here as well because there are many cases where vanilla modifies stack directly without and inventory handler isn't aware of it
					// however it does notify the slot of change
					backPackWrapper.getInventoryHandler().saveInventory();
				}
			});

			slotIndex++;
			if (slotIndex % getSlotsOnLine() == 0) {
				yPosition += 18;
			}
		}

		return yPosition;
	}

	private void addPlayerInventorySlots(PlayerInventory playerInventory, int yPosition, int slotIndex, boolean lockBackpackSlot) {
		int playerInventoryYOffset = backPackWrapper.getScreenProperties().getPlayerInventoryYOffset();

		yPosition += 14;

		for (int i = 0; i < 3; ++i) {
			for (int j = 0; j < 9; ++j) {
				addSlot(new Slot(playerInventory, j + i * 9 + 9, playerInventoryYOffset + 8 + j * 18, yPosition));
			}
			yPosition += 18;
		}

		yPosition += 4;

		for (int k = 0; k < 9; ++k) {
			Slot slot = addSlot(new Slot(playerInventory, k, playerInventoryYOffset + 8 + k * 18, yPosition));
			if (lockBackpackSlot && k == slotIndex) {
				backpackSlotNumber = slot.slotNumber;
			}
		}
	}

	public int getNumberOfRows() {
		BackpackInventoryHandler invHandler = backPackWrapper.getInventoryHandler();
		return (int) Math.ceil((double) invHandler.getSlots() / getSlotsOnLine());
	}

	private int getSlotsOnLine() {
		return backPackWrapper.getScreenProperties().getSlotsOnLine();
	}

	@Override
	public boolean canInteractWith(PlayerEntity playerIn) {
		return true;
	}

	public static BackpackContainer fromBufferItem(int windowId, PlayerInventory playerInventory, PacketBuffer packetBuffer) {
		return new BackpackContainer(windowId, playerInventory.player, packetBuffer.readString(), packetBuffer.readInt());
	}

	public static BackpackContainer fromBufferBlock(int windowId, PlayerInventory playerInventory, PacketBuffer packetBuffer) {
		return new BackpackContainer(windowId, playerInventory.player, BlockPos.fromLong(packetBuffer.readLong()));
	}

	@Override
	public ItemStack transferStackInSlot(PlayerEntity playerIn, int index) {
		ItemStack itemstack = ItemStack.EMPTY;
		Slot slot = inventorySlots.get(index);
		if (slot != null && slot.getHasStack()) {
			ItemStack slotStack = slot.getStack();

			if (index == backpackSlotNumber) {
				return ItemStack.EMPTY;
			}
			itemstack = slotStack.copy();
			int backpackSlots = getBackpackSlotsCount();
			if (index < backpackSlots + getNumberOfUpgradeSlots()) {
				if (!mergeItemStack(slotStack, backpackSlots + getNumberOfUpgradeSlots(), getFirstUpgradeSettingsSlot(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!mergeItemStack(slotStack, backpackSlots, backpackSlots + getNumberOfUpgradeSlots(), false)
					&& !mergeItemStack(slotStack, 0, backpackSlots, false)) {
				return ItemStack.EMPTY;
			}

			if (slotStack.isEmpty()) {
				slot.putStack(ItemStack.EMPTY);
			} else {
				slot.onSlotChanged();
			}
		}

		return itemstack;
	}

	private int getBackpackSlotsCount() {
		return getNumberOfRows() * getSlotsOnLine();
	}

	@Override
	public ItemStack slotClick(int slotId, int dragType, ClickType clickType, PlayerEntity player) {
		if (slotId == backpackSlotNumber) {
			return ItemStack.EMPTY;
		} else if (slotId >= getFirstUpgradeSettingsSlot()) {
			ItemStack currentStack = player.inventory.getItemStack().copy();
			if (currentStack.getCount() > 1) {
				currentStack.setCount(1);
			}
			getSlot(slotId).putStack(currentStack);
			return ItemStack.EMPTY;
		}
		return super.slotClick(slotId, dragType, clickType, player);
	}

	@Override
	public boolean canMergeSlot(ItemStack stack, Slot slotIn) {
		return slotIn.slotNumber < getFirstUpgradeSettingsSlot();
	}

	public int getNumberOfSlots() {
		return backPackWrapper.getInventoryHandler().getSlots();
	}

	public ScreenProperties getScreenProperties() {
		return backPackWrapper.getScreenProperties();
	}

	public int getNumberOfUpgradeSlots() {
		return backPackWrapper.getUpgradeHandler().getSlots();
	}

	@Override
	public void onContainerClosed(PlayerEntity playerIn) {
		super.onContainerClosed(playerIn);
		BackpackInventoryEventBus.unregisterListener(playerIn.getUniqueID());
	}

	public int getFirstUpgradeSettingsSlot() {
		return getNumberOfSlots() + getNumberOfUpgradeSlots() + NUMBER_OF_PLAYER_SLOTS;
	}

	public Map<ResourceLocation, UpgradeContainerBase> getUpgradeContainers() {
		return upgradeContainers;
	}
}
