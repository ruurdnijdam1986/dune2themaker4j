package com.fundynamic.d2tm.game.entities.units;

import com.fundynamic.d2tm.game.behaviors.*;
import com.fundynamic.d2tm.game.entities.*;
import com.fundynamic.d2tm.game.entities.predicates.PredicateBuilder;
import com.fundynamic.d2tm.game.entities.projectiles.Projectile;
import com.fundynamic.d2tm.game.map.Map;
import com.fundynamic.d2tm.game.rendering.RenderQueue;
import com.fundynamic.d2tm.math.Random;
import com.fundynamic.d2tm.math.Vector2D;
import org.newdawn.slick.Graphics;
import org.newdawn.slick.Image;
import org.newdawn.slick.SpriteSheet;

import static com.fundynamic.d2tm.Game.TILE_SIZE;

public class Unit extends Entity implements Selectable, Moveable, Destructible, Destroyer {

    // Behaviors
    private FadingSelection fadingSelection;

    // use contexts!?
    protected final HitPointBasedDestructibility hitPointBasedDestructibility;

    private Vector2D target;
    private Vector2D nextTargetToMoveTo;

    // Dependencies
    private final Map map;


    // Drawing 'movement' from cell to cell
    private Vector2D offset;
    private float facing;
    private int desiredFacing;

    private boolean hasSpawnedExplosions;

    public Unit(Map map, Vector2D absoluteMapCoordinates, SpriteSheet spriteSheet, Player player, EntityData entityData, EntityRepository entityRepository) {
        this(
                map,
                absoluteMapCoordinates,
                spriteSheet,
                new FadingSelection(entityData.width, entityData.height),
                new HitPointBasedDestructibility(entityData.hitPoints, entityData.width),
                player,
                entityData,
                entityRepository
        );
    }

    // TODO: Simplify constructor
    public Unit(Map map, Vector2D absoluteMapCoordinates, SpriteSheet spriteSheet, FadingSelection fadingSelection, HitPointBasedDestructibility hitPointBasedDestructibility, Player player, EntityData entityData, EntityRepository entityRepository) {
        super(absoluteMapCoordinates, spriteSheet, entityData, player, entityRepository);
        this.map = map;

        int possibleFacings = spriteSheet.getHorizontalCount();
        this.facing = Random.getRandomBetween(0, possibleFacings);
        this.desiredFacing = (int) facing;
        this.fadingSelection = fadingSelection;
        this.hitPointBasedDestructibility = hitPointBasedDestructibility;
        this.target = absoluteMapCoordinates;
        this.nextTargetToMoveTo = absoluteMapCoordinates;
        this.offset = Vector2D.zero();
        if (entityData.moveSpeed < 0.0001f) {
            throw new IllegalArgumentException("The speed of this unit is so slow, you must be joking right? - given moveSpeed is " + entityData.moveSpeed);
        }
    }

    @Override
    public void render(Graphics graphics, int x, int y) {
        if (graphics == null) throw new IllegalArgumentException("Graphics must be not-null");
        Image sprite = getSprite();
        // todo: GET RID OF OFFSET
        int drawY = y + offset.getYAsInt();
        int drawX = x + offset.getXAsInt();
        graphics.drawImage(sprite, drawX, drawY);
    }

    @Override
    public void update(float deltaInSeconds) {
        if (this.isDestroyed()) {
            return;
        }

        this.fadingSelection.update(deltaInSeconds);
        if (needsToBeSomewhereElse()) {
            if (hasNoNextCellToMoveTo()) {
                decideWhatCellToMoveToNextOrStopMovingWhenNotPossible();
            } else {
                if (desiredFacing == (int) facing) {
                    moveToNextCellPixelByPixel(deltaInSeconds);
                } else {
                    facing = UnitFacings.turnTo(facing, desiredFacing, entityData.getRelativeTurnSpeed(deltaInSeconds));
                }
            }
        }

        if (hitPointBasedDestructibility.hasDied()) {
            hasSpawnedExplosions = true;
            entityRepository.explodeAtCell(absoluteCoordinates, entityData.explosionId, player);
        }
    }

    private void moveToNextCellPixelByPixel(float deltaInSeconds) {
        float offsetX = offset.getX();
        float offsetY = offset.getY();

        if (nextTargetToMoveTo.getXAsInt() < absoluteCoordinates.getXAsInt()) offsetX -= entityData.getRelativeMoveSpeed(deltaInSeconds);
        if (nextTargetToMoveTo.getXAsInt() > absoluteCoordinates.getXAsInt()) offsetX += entityData.getRelativeMoveSpeed(deltaInSeconds);
        if (nextTargetToMoveTo.getYAsInt() < absoluteCoordinates.getYAsInt()) offsetY -= entityData.getRelativeMoveSpeed(deltaInSeconds);
        if (nextTargetToMoveTo.getYAsInt() > absoluteCoordinates.getYAsInt()) offsetY += entityData.getRelativeMoveSpeed(deltaInSeconds);

        Vector2D vecToAdd = Vector2D.zero();
        if (offsetX > 31) {
            offsetX = 0;
            vecToAdd = vecToAdd.add(Vector2D.create(32, 0));
        }
        if (offsetX < -31) {
            offsetX = 0;
            vecToAdd = vecToAdd.add(Vector2D.create(-32, 0));
        }
        if (offsetY > 31) {
            offsetY = 0;
            vecToAdd = vecToAdd.add(Vector2D.create(0, 32));
        }
        if (offsetY < -31) {
            offsetY = 0;
            vecToAdd = vecToAdd.add(Vector2D.create(0, -32));
        }

        // Arrived at intended next target cell
        if (!vecToAdd.equals(Vector2D.zero())) {
            moveToCell(absoluteCoordinates.add(vecToAdd));
        }
        offset = Vector2D.create(offsetX, offsetY);
    }

    private void decideWhatCellToMoveToNextOrStopMovingWhenNotPossible() {
        int nextXCoordinate = absoluteCoordinates.getXAsInt();
        int nextYCoordinate = absoluteCoordinates.getYAsInt();
        if (target.getXAsInt() < absoluteCoordinates.getXAsInt()) nextXCoordinate -= TILE_SIZE;
        if (target.getXAsInt() > absoluteCoordinates.getXAsInt()) nextXCoordinate += TILE_SIZE;
        if (target.getYAsInt() < absoluteCoordinates.getYAsInt()) nextYCoordinate -= TILE_SIZE;
        if (target.getYAsInt() > absoluteCoordinates.getYAsInt()) nextYCoordinate += TILE_SIZE;

        Vector2D intendedMapCoordinatesToMoveTo = new Vector2D(nextXCoordinate, nextYCoordinate);

        EntitiesSet entities = entityRepository.findEntitiesOfTypeAtVector(intendedMapCoordinatesToMoveTo, EntityType.UNIT);

        if (entities.isEmpty() && !UnitMoveIntents.hasIntentFor(intendedMapCoordinatesToMoveTo)) {
            this.nextTargetToMoveTo = intendedMapCoordinatesToMoveTo;
            this.desiredFacing = determineFacingFor(nextTargetToMoveTo).getValue();
            UnitMoveIntents.addIntent(nextTargetToMoveTo);
        }
    }

    private boolean hasNoNextCellToMoveTo() {
        return nextTargetToMoveTo == absoluteCoordinates;
    }

    private void moveToCell(Vector2D vectorToMoveTo) {
        this.absoluteCoordinates = vectorToMoveTo;
        this.nextTargetToMoveTo = vectorToMoveTo;

        UnitMoveIntents.removeIntent(vectorToMoveTo);

        // TODO: replace with some event "unit moved to coordinate" which is picked up
        // elsewhere (Listener?)
        map.revealShroudFor(absoluteCoordinates, getSight(), player);
    }

    private boolean needsToBeSomewhereElse() {
        return !this.target.equals(absoluteCoordinates);
    }

    public Image getSprite() {
        return spriteSheet.getSprite((int) facing, 0);
    }

    @Override
    public String toString() {
        return "Unit [" +
                "sight=" + getSight() +
                ", player=" + super.player +
                ", facing=" + facing +
                ", hitPoints=" + hitPointBasedDestructibility +
                ", absoluteCoordinates=" + super.absoluteCoordinates +
                "]\n";
    }

    /**
     * Unit is marked as selected. To get all selected units for player, use the com.fundynamic.d2tm.game.entities.EntityRepository#filter(Predicate) and
     * along with a {@link PredicateBuilder#isSelected()}
     */
    @Override
    public void select() {
        fadingSelection.select();
    }

    @Override
    public void deselect() {
        fadingSelection.deselect();
    }

    @Override
    public boolean isSelected() {
        return fadingSelection.isSelected();
    }

    @Override
    public void moveTo(Vector2D absoluteMapCoordinates) {
        this.target = absoluteMapCoordinates;
    }

    private UnitFacings determineFacingFor(Vector2D coordinatesToFaceTo) {
       return UnitFacings.getFacing(absoluteCoordinates, coordinatesToFaceTo);
    }

    @Override
    public void takeDamage(int hitPoints) {
        hitPointBasedDestructibility.takeDamage(hitPoints);
    }

    @Override
    public boolean isDestroyed() {
        return hasSpawnedExplosions && hitPointBasedDestructibility.hasDied();
    }

    @Override
    public int getHitPoints() {
        return hitPointBasedDestructibility.getHitPoints();
    }

    @Override
    public void attack(Entity entity) {
        if (!entity.isDestructible()) {
            System.out.println("I (" + this.toString() + ") attack an entity that is not destructible -> " + entity);
            return;
        }

        if (entityData.hasWeaponId()) {
            Projectile projectile = (Projectile) entityRepository.placeOnMap(absoluteCoordinates, EntityType.PROJECTILE, entityData.weaponId, player);
            projectile.moveTo(entity.getRandomPositionWithin());
        }
    }

    public Vector2D getOffset() {
        return offset;
    }

    public Vector2D getNextTargetToMoveTo() {
        return nextTargetToMoveTo;
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.UNIT;
    }

    public void setFacing(int facing) {
        this.facing = facing;
    }

    public void setOffset(Vector2D offset) {
        this.offset = offset;
    }

    public void setFadingSelection(FadingSelection fadingSelection) {
        this.fadingSelection = fadingSelection;
    }

    @Override
    public void enrichRenderQueue(RenderQueue renderQueue) {
        if (isSelected()) {
            renderQueue.putEntityGui(this.hitPointBasedDestructibility, this.getAbsoluteCoordinates().add(this.offset));
            renderQueue.putEntityGui(this.fadingSelection, this.getAbsoluteCoordinates().add(this.offset));
        }
    }
}
