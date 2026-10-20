# AtlasProjectiles

A small Minestom projectile library providing consistent launch, flight, and
collision behavior through a focused API.

Supported behaviors:

- arrows: triangular launch uncertainty, air/water drag, `0.05` gravity, critical
  metadata, block embedding, and 1,200-tick in-ground expiry;
- thrown items: `0.03` gravity and air/water drag;
- fireballs: directional acceleration and `0.95` inertia, both
  configurable;
- shulker bullets: axis-based path selection and steering;
- configurable smooth homing projectiles;
- bow charge handling and firing sound through `BowModule`.

The library owns projectile movement and collision detection. Game-specific hit
effects such as damage, explosions, teleportation, potion effects, pickup rules,
and enchantments belong in Minestom collision-event listeners.

## Installation

```kotlin
repositories {
    maven("https://reposilite.atlasengine.ca/public")
}

dependencies {
    implementation("ca.atlasengine:atlas-projectiles:3.0.1")
}
```

Published versions are available from the
[AtlasEngine repository](https://reposilite.atlasengine.ca/#/public/ca/atlasengine/atlas-projectiles).

## API

`power` and `uncertainty` use the library's projectile units. A fully charged bow uses `3.0`
power and `1.0` uncertainty. Minestom entity velocity remains in blocks per
second.

```java
Point origin = player.getPosition().add(0.0, player.getEyeHeight() - 0.1, 0.0);

// Shoot in the player's look direction and inherit player movement.
var arrow = new ArrowProjectile(EntityType.ARROW, player);
arrow.setCritical(true);
arrow.shoot(origin, 3.0, 1.0);

// Aim at an exact point without inheriting shooter movement.
var snowball = new ThrownItemProjectile(EntityType.SNOWBALL, player);
snowball.shoot(origin, target.getPosition(), 1.5, 1.0);

// Apply an upward pitch offset.
var potion = new ThrownItemProjectile(EntityType.SPLASH_POTION, player);
potion.shootFromRotation(origin, -20.0f, 0.5, 1.0);

// A shulker bullet begins at rest and selects its own route.
var bullet = new ShulkerBulletProjectile(EntityType.SHULKER_BULLET, player, target);
bullet.spawn(origin);
```

Install bow behavior on any entity event node:

```java
new BowModule(player.eventNode(),
        (shooter, bow) -> new ArrowProjectile(EntityType.ARROW, shooter));
```

Listen for `ProjectileCollideWithEntityEvent` and
`ProjectileCollideWithBlockEvent` to implement hit effects. Cancelling either
event allows the projectile to continue. By default, thrown items, fireballs,
and homing projectiles are removed on impact; arrows embed in blocks.

The runnable server example is in
[`src/test/java/Main.java`](src/test/java/Main.java).

## Building

Java 25 is required.

```shell
./gradlew test javadoc
```

## License

Apache-2.0. See [`LICENSE`](LICENSE).
