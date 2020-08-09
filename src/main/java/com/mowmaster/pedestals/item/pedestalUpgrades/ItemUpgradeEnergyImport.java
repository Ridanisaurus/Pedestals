package com.mowmaster.pedestals.item.pedestalUpgrades;

import com.mowmaster.pedestals.tiles.TilePedestal;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.List;

import static com.mowmaster.pedestals.pedestals.PEDESTALS_TAB;
import static com.mowmaster.pedestals.references.Reference.MODID;

public class ItemUpgradeEnergyImport extends ItemUpgradeBaseEnergy
{

    public ItemUpgradeEnergyImport(Properties builder) {super(builder.group(PEDESTALS_TAB));}

    @Override
    public Boolean canAcceptCapacity() {
        return true;
    }

    @Override
    public boolean canSendItem(TilePedestal tile)
    {
        return tile.getStoredValueForUpgrades()>0;
    }

    public void updateAction(int tick, World world, ItemStack itemInPedestal, ItemStack coinInPedestal, BlockPos pedestalPos)
    {
        if(!world.isRemote)
        {
            //Still Needed as we want to limit energy transfer from world to PEN
            int speed = getOperationSpeed(coinInPedestal);

            if(!world.isBlockPowered(pedestalPos))
            {
                //Always send energy, as fast as we can within the Pedestal Energy Network
                upgradeActionSendEnergy(world,coinInPedestal,pedestalPos);
                if (tick%speed == 0) {
                    upgradeItemAction(world,pedestalPos,itemInPedestal,coinInPedestal);
                    upgradeAction(world,pedestalPos,itemInPedestal,coinInPedestal);
                }
            }
        }
    }

    public void upgradeAction(World world, BlockPos posOfPedestal, ItemStack itemInPedestal, ItemStack coinInPedestal)
    {
        int getMaxEnergyValue = getEnergyBuffer(coinInPedestal);
        if(!hasMaxEnergySet(coinInPedestal) || readMaxEnergyFromNBT(coinInPedestal) != getMaxEnergyValue) {setMaxEnergy(coinInPedestal, getMaxEnergyValue);}

        BlockPos posInventory = getPosOfBlockBelow(world,posOfPedestal,1);
        ItemStack itemFromPedestal = ItemStack.EMPTY;

        LazyOptional<IEnergyStorage> cap = findEnergyHandlerAtPos(world,posInventory,getPedestalFacing(world, posOfPedestal),true);

        //Gets inventory TE then makes sure its not a pedestal
        TileEntity invToPushTo = world.getTileEntity(posInventory);
        if(invToPushTo instanceof TilePedestal) {
            itemFromPedestal = ItemStack.EMPTY;
        }
        else {
            if(cap.isPresent())
            {
                IEnergyStorage handler = cap.orElse(null);

                if(handler != null)
                {
                    if(handler.canExtract())
                    {
                        int containerCurrentEnergy = handler.getEnergyStored();
                        int getMaxEnergy = getMaxEnergyValue;
                        int getCurrentEnergy = getEnergyStored(coinInPedestal);
                        int getSpaceForEnergy = getMaxEnergy - getCurrentEnergy;
                        int transferRate = (getSpaceForEnergy >= getEnergyTransferRate(coinInPedestal))?(getEnergyTransferRate(coinInPedestal)):(getSpaceForEnergy);
                        if (containerCurrentEnergy < transferRate) {transferRate = containerCurrentEnergy;}

                        //transferRate at this point is equal to what we can send.
                        if(handler.extractEnergy(transferRate,true) > 0)
                        {
                            int energyRemainingInUpgrade = getCurrentEnergy + transferRate;
                            setEnergyStored(coinInPedestal,energyRemainingInUpgrade);
                            handler.extractEnergy(transferRate,false);
                        }
                    }
                }
            }
        }
    }

    public void upgradeItemAction(World world, BlockPos posOfPedestal, ItemStack itemInPedestal, ItemStack coinInPedestal)
    {
        TileEntity tile = world.getTileEntity(posOfPedestal);
        if(tile instanceof TilePedestal)
        {
            TilePedestal ped = ((TilePedestal)tile);
            int getMaxEnergyValue = getEnergyBuffer(coinInPedestal);

            if(ped.hasItem())
            {
                if(isEnergyItemExtract(itemInPedestal))
                {
                    int itemCurrentEnergy = getEnergyInStack(itemInPedestal);
                    int getMaxEnergy = getMaxEnergyValue;
                    int getCurrentEnergy = getEnergyStored(coinInPedestal);
                    int getSpaceForEnergy = getMaxEnergy - getCurrentEnergy;
                    int transferRate = (getSpaceForEnergy >= getEnergyTransferRate(coinInPedestal))?(getEnergyTransferRate(coinInPedestal)):(getSpaceForEnergy);
                    if (itemCurrentEnergy < transferRate) {transferRate = itemCurrentEnergy;}

                    if(extractEnergyFromStack(itemInPedestal,transferRate,true)>0)
                    {
                        int energyRemainingInUpgrade = getCurrentEnergy + transferRate;
                        setEnergyStored(coinInPedestal,energyRemainingInUpgrade);
                        extractEnergyFromStack(itemInPedestal,transferRate,false);
                        ped.setStoredValueForUpgrades(0);
                        ped.update();
                    }
                }
                else
                {
                    ped.setStoredValueForUpgrades(1);
                    ped.update();
                }
            }
        }
    }

    public static int getItemRFChargeAmount(ItemStack chargeItem)
    {
        int charge = 0;
        if(chargeItem.getItem().equals(Items.REDSTONE))
        {
            return 2500;
        }
        else if(chargeItem.getItem().equals(Items.REDSTONE_BLOCK))
        {
            return 22500;
        }

        return charge;
    }

    @Override
    public void actionOnCollideWithBlock(World world, TilePedestal tilePedestal, BlockPos posPedestal, BlockState state, Entity entityIn)
    {
        if(!world.isRemote)
        {
            if(!world.isBlockPowered(posPedestal))
            {
                if(entityIn instanceof ItemEntity)
                {
                    ItemStack getItemStack = ((ItemEntity) entityIn).getItem();
                    ItemStack coin = tilePedestal.getCoinOnPedestal();
                    int getMaxEnergyValue = getEnergyBuffer(coin);
                    int currentEnergy = getEnergyStored(coin);
                    int chargeAmount = getItemRFChargeAmount(getItemStack);
                    int getRFForStack = chargeAmount * getItemStack.getCount();
                    if(chargeAmount>0 && getMaxEnergyValue>=(currentEnergy+getRFForStack))
                    {
                        setEnergyStored(coin,(currentEnergy + getRFForStack));
                        tilePedestal.update();
                        world.playSound((PlayerEntity) null, posPedestal.getX(), posPedestal.getY(), posPedestal.getZ(), SoundEvents.BLOCK_REDSTONE_TORCH_BURNOUT, SoundCategory.BLOCKS, 0.25F, 1.0F);
                        entityIn.remove();
                    }
                }
            }
        }
    }

    public static final Item RFIMPORT = new ItemUpgradeEnergyImport(new Properties().maxStackSize(64).group(PEDESTALS_TAB)).setRegistryName(new ResourceLocation(MODID, "coin/rfimport"));

    @SubscribeEvent
    public static void onItemRegistryReady(RegistryEvent.Register<Item> event)
    {
        event.getRegistry().register(RFIMPORT);
    }


}