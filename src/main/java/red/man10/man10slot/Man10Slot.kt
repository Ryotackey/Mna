package red.man10.man10slot

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import red.man10.kotlin.CustomConfig
import red.man10.man10vaultapiplus.MoneyPoolObject
import red.man10.man10vaultapiplus.enums.MoneyPoolTerm
import red.man10.man10vaultapiplus.enums.MoneyPoolType
import java.io.File
import kotlin.collections.HashMap

class Man10Slot : JavaPlugin() {

    var enable = true

    val loccon = CustomConfig(this, "location.yml")

    var slotfile = File(this.dataFolder.absolutePath + "\\slots")

    val slotmap = HashMap<String, SlotInformation>()

    val buttonselectmap = HashMap<Player, MutableList<String>>()
    val frameselectmap = HashMap<Player, MutableList<String>>()
    val leverselect = HashMap<Player, String>()
    val lightselect = HashMap<Player, String>()
    val signselect = HashMap<Player, String>()

    var button1loc = HashMap<Location, String>()
    var button2loc = HashMap<Location, String>()
    var button3loc = HashMap<Location, String>()
    val frameloc = HashMap<String, MutableList<Location>>()
    var leverloc = HashMap<Location, String>()
    val signloc = HashMap<String, Location>()
    val lightloc = HashMap<String, Location>()

    val invmap = HashMap<Player, MutableList<Inventory>>()

    val prefix = "§l[§d§lMa§f§ln§a§l10§e§lSlot§f§l]"

    override fun onEnable() {
        // Plugin startup logic

        loccon.saveDefaultConfig()

        slotfile.mkdir()

        slotLoad()

        loadLocation()

        if (loccon.getConfig()!!.contains("enable")){
            enable = loccon.getConfig()!!.getBoolean("enable")
        }

        server.pluginManager.registerEvents(Event(this), this)
        getCommand("mslot").executor = Command(this)
    }

    override fun onDisable() {
        // Plugin shutdown logic
        locationSave()
        for (i in slotmap){

            val slot = i.value
            slot.spin1 = false
            slot.spin2 = false
            slot.spin3 = false
            if (slot.block != null){
                val b = lightloc[i.key]!!.block
                b.type = slot.block
            }

        }
    }

    fun locationSave(){

        val config = loccon.getConfig() ?: return

        saveLocationString(config, "button1loc", button1loc)
        saveLocationString(config, "button2loc", button2loc)
        saveLocationString(config, "button3loc", button3loc)

        saveLocationString(config, "leverloc", leverloc)

        for (i in signloc){
            val loc = "${i.value.blockX}/${i.value.blockY}/${i.value.blockZ}/${i.value.world.name}"
            config.set("location.signloc.${i.key}", loc)
        }

        for (i in lightloc){
            val loc = "${i.value.blockX}/${i.value.blockY}/${i.value.blockZ}/${i.value.world.name}"
            config.set("location.lightloc.${i.key}", loc)
        }

        for (i in frameloc){

            val list = mutableListOf<String>()

            for (j in i.value){
                val loc = "${j.blockX}/${j.blockY}/${j.blockZ}/${j.world.name}"
                list.add(loc)
            }

            config.set("location.frameloc.${i.key}", list)

        }

        for (i in slotmap){
            config.set("poolid.${i.key}", i.value.pool!!.id)
            config.set("spincount.${i.key}", i.value.spincount)
            config.set("chancenum.${i.key}", i.value.chancenum)
            for (j in i.value.wincount){
                config.set("wincount.${i.key}.${j.key}", j.value)
            }
        }

        loccon.saveConfig()

    }

    fun saveLocationString(config: FileConfiguration, name: String, map: HashMap<Location, String>){
        for (i in map){
            val loc = "${i.key.blockX}/${i.key.blockY}/${i.key.blockZ}/${i.key.world.name}"
            config.set("location.$name.${i.value}", loc)
        }
    }

    fun loadLocation(){

        val config = loccon.getConfig() ?: return

        if (!config.contains("location")){
            return
        }

        button1loc = loadLocationString(config, "button1loc")
        button2loc = loadLocationString(config, "button2loc")
        button3loc = loadLocationString(config, "button3loc")

        leverloc = loadLocationString(config, "leverloc")

        for (key in config.getConfigurationSection("location.signloc").getKeys(false)){

            val loc = strToLocation(config.getString("location.signloc.$key"))

            signloc[key] = loc
        }

        for (key in config.getConfigurationSection("location.lightloc").getKeys(false)){

            val loc = strToLocation(config.getString("location.lightloc.$key"))

            lightloc[key] = loc
        }

        for (key in config.getConfigurationSection("location.frameloc").getKeys(false)){

            val loclist = mutableListOf<Location>()

            for (i in config.getList("location.frameloc.$key") as MutableList<String>) {
                val loc = strToLocation(i)
                loclist.add(loc)
            }

            frameloc[key] = loclist
        }

    }

    fun loadLocationString(config: FileConfiguration, name: String): HashMap<Location, String>{

        val map = HashMap<Location, String>()

        for (key in config.getConfigurationSection("location.$name").getKeys(false)){

            val loc = strToLocation(config.getString("location.$name.$key"))

            map[loc] = key
        }
        return map
    }

    fun removeLocation(key: String){

        button1loc.values.remove(key)
        button2loc.values.remove(key)
        button3loc.values.remove(key)
        leverloc.values.remove(key)

        signloc.remove(key)
        lightloc.remove(key)
        frameloc.remove(key)

        for (i in slotmap){
            if (i.key == key){
                i.value.wincount.clear()
                i.value.spincount = 0
            }
        }

        for (i in loccon.getConfig()!!.getKeys(false)) {
            loccon.getConfig()!!.set(i, null)
        }

        locationSave()

    }

    fun strToLocation(str: String): Location{

        val locstr = str.split(Regex("/"))

        val loc = Location(Bukkit.getWorld(locstr[3]), locstr[0].toDouble(), locstr[1].toDouble(), locstr[2].toDouble())

        return loc
    }

    fun slotLoad(){

        logger.info("スロットデータのロードを開始します")

        val filelist = slotfile.listFiles()

        val slotlist = fileToConfig(filelist)

        if (slotlist.size == 0)return

        for (config in slotlist){
            for (key in config.getConfigurationSection("").getKeys(false)) {
                val slot = SlotInformation()

                slot.slot_name = config.getString("$key.general_setting.slot_name")
                slot.price = config.getDouble("$key.general_setting.price")

                val reel1 = stringToItemList(config.getString("$key.general_setting.items_reel1"))
                val reel2 = stringToItemList(config.getString("$key.general_setting.items_reel2"))
                val reel3 = stringToItemList(config.getString("$key.general_setting.items_reel3"))

                slot.reel1 = reel1
                slot.reel2 = reel2
                slot.reel3 = reel3

                slot.stock = config.getDouble("$key.general_setting.stock")

                if (config.contains("$key.spin_setting")){
                    val effect = Effect()
                    if (config.contains("$key.spin_setting.sound")){
                        val sound1 = Sound()
                        sound1.sound = org.bukkit.Sound.valueOf(config.getString("$key.spin_setting.sound.sound"))
                        sound1.volume = config.getDouble("$key.spin_setting.sound.volume").toFloat()
                        sound1.pitch = config.getDouble("$key.spin_setting.sound.pitch").toFloat()
                        effect.sound = sound1
                    }
                    if (config.contains("$key.spin_setting.particle")){
                        val par = Particle()
                        par.par = org.bukkit.Particle.valueOf(config.getString("$key.spin_setting.particle.particle"))
                        par.count = config.getInt("$key.spin_setting.particle.count")
                        par.chance = config.getDouble("$key.spin_setting.particle.chance")
                        effect.particle = par
                    }
                    if (config.contains("$key.spin_setting.command")){
                        effect.command = config.getList("$key.spin_setting.command") as MutableList<String>
                    }
                    slot.spineffect = effect
                }



                if (loccon.getConfig()!!.contains("chancenum.$key")){
                    slot.chancenum = loccon.getConfig()!!.getInt("chancenum.$key")
                }

                for (win in config.getConfigurationSection("$key.wining_setting").getKeys(false)){

                    slot.wining_chance[win] = config.getList("$key.wining_setting.$win.chance") as MutableList<Double>

                    slot.wining_name[win] = config.getString("$key.wining_setting.$win.name")
                    slot.wining_item[win] = wining_itemCreate(config.getString("$key.wining_setting.$win.item"), reel1, reel2, reel3)
                    slot.wining_prize[win] = config.getDouble("$key.wining_setting.$win.prize")
                    slot.wining_stockodds[win] = config.getDouble("$key.wining_setting.$win.stockodds")
                    slot.wining_step[win] = config.getInt("$key.wining_setting.$win.step")
                    slot.wining_con[win] = config.getBoolean("$key.wining_setting.$win.flag_con")
                    slot.wining_light[win] = config.getBoolean("$key.wining_setting.$win.light")

                    if (config.contains("$key.wining_setting.$win.chancechange")){
                        slot.chancechange[win] = config.getInt("$key.wining_setting.$win.chancechange")
                    }else slot.chancechange[win] = null

                    if (config.contains("$key.wining_setting.$win.changegame")){
                        slot.changegame[win] = config.getInt("$key.wining_setting.$win.changegame")
                    }else slot.changegame[win] = -1

                    if (config.contains("$key.wining_setting.$win.command")) {
                        slot.wining_command[win] = config.getList("$key.wining_setting.$win.command") as MutableList<String>
                    }

                    if (config.contains("$key.wining_setting.$win.chancewin")){
                        slot.chancewin[win] = config.getList("$key.wining_setting.$win.chancewin") as MutableList<String>
                    }

                    if (config.contains("$key.wining_setting.$win.lightsound")){
                        val sound = Sound()
                        sound.sound = org.bukkit.Sound.valueOf(config.getString("$key.wining_setting.$win.lightsound.sound"))
                        sound.volume = config.getDouble("$key.wining_setting.$win.lightsound.volume").toFloat()
                        sound.pitch = config.getDouble("$key.wining_setting.$win.lightsound.pitch").toFloat()

                        slot.wining_lightsound[win] = sound
                    }

                    if (config.contains("$key.wining_setting.$win.win_particle")){
                        val par = Particle()
                        par.par = org.bukkit.Particle.valueOf(config.getString("$key.wining_setting.$win.win_particle.particle"))
                        par.count = config.getInt("$key.wining_setting.$win.win_particle.count")
                        par.chance = config.getDouble("$key.wining_setting.$win.win_particle.chance")
                        slot.win_particle[win] = par
                    }

                    if (config.contains("$key.wining_setting.$win.light_particle")){
                        val par = Particle()
                        par.par = org.bukkit.Particle.valueOf(config.getString("$key.wining_setting.$win.light_particle.particle"))
                        par.count = config.getInt("$key.wining_setting.$win.light_particle.count")
                        par.chance = config.getDouble("$key.wining_setting.$win.light_particle.chance")
                        slot.light_particle[win] = par
                    }

                    val sound = Sound()
                    sound.sound = org.bukkit.Sound.valueOf(config.getString("$key.wining_setting.$win.winsound.sound"))
                    sound.volume = config.getDouble("$key.wining_setting.$win.winsound.volume").toFloat()
                    sound.pitch = config.getDouble("$key.wining_setting.$win.winsound.pitch").toFloat()

                    slot.wining_sound[win] = sound

                }

                if (loccon.getConfig()!!.contains("poolid.$key")){
                    slot.pool = MoneyPoolObject("Man10Slot", loccon.getConfig()!!.getLong("poolid.$key"))
                }else{
                    slot.pool = MoneyPoolObject("Man10Slot", MoneyPoolTerm.LONG_TERM, MoneyPoolType.GAMBLE_POOL, "$key pool")
                }

                if (loccon.getConfig()!!.contains("spincount.$key")){
                    slot.spincount = loccon.getConfig()!!.getInt("spincount.$key")
                }

                if (loccon.getConfig()!!.contains("wincount.$key")){
                    for (i in loccon.getConfig()!!.getConfigurationSection("wincount.${key}").getKeys(false)){
                        slot.wincount[i] = loccon.getConfig()!!.getInt("wincount.${key}.$i")
                    }
                }

                if (config.contains("$key.stop_setting")){
                    val effect = Effect()
                    if (config.contains("$key.stop_setting.sound")){
                        val sound1 = Sound()
                        sound1.sound = org.bukkit.Sound.valueOf(config.getString("$key.stop_setting.sound.sound"))
                        sound1.volume = config.getDouble("$key.stop_setting.sound.volume").toFloat()
                        sound1.pitch = config.getDouble("$key.stop_setting.sound.pitch").toFloat()
                        effect.sound = sound1
                    }
                    if (config.contains("$key.stop_setting.particle")){
                        val par = Particle()
                        par.par = org.bukkit.Particle.valueOf(config.getString("$key.stop_setting.particle.particle"))
                        par.count = config.getInt("$key.stop_setting.particle.count")
                        par.chance = config.getDouble("$key.stop_setting.particle.chance")
                        effect.particle = par
                    }
                    if (config.contains("$key.stop_setting.command")){
                        effect.command = config.getList("$key.stop_setting.command") as MutableList<String>
                    }
                    slot.stopeffect = effect
                }

                if (config.contains("$key.change_setting")){
                    val effect = Effect()
                    if (config.contains("$key.change_setting.sound")){
                        val sound1 = Sound()
                        sound1.sound = org.bukkit.Sound.valueOf(config.getString("$key.change_setting.sound.sound"))
                        sound1.volume = config.getDouble("$key.change_setting.sound.volume").toFloat()
                        sound1.pitch = config.getDouble("$key.change_setting.sound.pitch").toFloat()
                        effect.sound = sound1
                    }
                    if (config.contains("$key.change_setting.particle")){
                        val par = Particle()
                        par.par = org.bukkit.Particle.valueOf(config.getString("$key.change_setting.particle.particle"))
                        par.count = config.getInt("$key.change_setting.particle.count")
                        par.chance = config.getDouble("$key.change_setting.particle.chance")
                        effect.particle = par
                    }
                    if (config.contains("$key.change_setting.command")){
                        effect.command = config.getList("$key.change_setting.command") as MutableList<String>
                    }
                    slot.changeeffect = effect
                }

                slotmap[key] = slot
                logger.info("${key}スロットロード完了")
            }

        }

    }

    fun stringToItemList(itemstr: String): MutableList<ItemStack>{

        val reel = mutableListOf<ItemStack>()

        if (!itemstr.contains(Regex(",")))return reel

        val item = itemstr.split(Regex(",")) as MutableList<String>

        for (i in item){

            val reelitem =if (i.contains("-")) `ItemStack+`(Material.getMaterial(i.split("-")[0].toInt()), i.split("-")[1].toShort())
            else `ItemStack+`(Material.getMaterial(i.toInt()))

            reel.add(reelitem.build())
        }
        return reel
    }

    fun wining_itemCreate(itemstr: String, reel1: MutableList<ItemStack>, reel2: MutableList<ItemStack>, reel3: MutableList<ItemStack>): MutableList<ItemStack>{

        val itemlist = mutableListOf<ItemStack>()

        if (!itemstr.contains(Regex(",")))return itemlist

        val item = itemstr.split(Regex(","))

        if (item.size == 3){

            for (i in 0 until item.size) {

                if (item[i] == "*") {
                    itemlist.add(ItemStack(Material.AIR))
                } else {

                    var num = 0
                    try {
                        num = item[i].toInt()
                    } catch (e: NumberFormatException) {
                        return itemlist
                    }

                    when (i % 3) {
                        0 -> itemlist.add(reel1[num - 1])
                        1 -> itemlist.add(reel2[num - 1])
                        2 -> itemlist.add(reel3[num - 1])
                    }
                }

            }

        }
        return itemlist
    }

    fun fileToConfig(list: Array<File>): MutableList<FileConfiguration>{

        val slotlist = mutableListOf<FileConfiguration>()

        for (i in list){

            if (!i.isFile)continue
            if (!i.path.endsWith(".yml"))continue

            val config = YamlConfiguration.loadConfiguration(i)
            slotlist.add(config)
        }
        return slotlist
    }

    fun mapSort(map: HashMap<Double, String>): MutableList<kotlin.collections.Map.Entry<Double, String>>{

        val list = mutableListOf<kotlin.collections.Map.Entry<Double, String>>()

        map.entries.stream()
                .sorted(java.util.Collections.reverseOrder(java.util.Map.Entry.comparingByKey<Double, String>()))
                .forEach { s -> list.add(s)}

        return list
    }

    fun runCommand(commands: MutableList<String>, winname: String, p: Player, slotname: String, money: Double){

        object : BukkitRunnable(){
            override fun run() {
                for (i in commands) {
                    val i2: String = if (i.contains(Regex("<player>"))){
                        i.replace("<player>", p.name)
                    }else i
                    val i3 = if (i2.contains(Regex("<slot>"))){
                        i.replace("<slot>", slotname)
                    }else i2
                    var i4= if (i3.contains(Regex("<prize>"))){
                        i.replace("<prize>", money.toString())
                    }else i3
                    var i5 = if (i4.contains(Regex("<win>"))){
                        i.replace("<win>", winname)
                    }else i4
                    server.dispatchCommand(server.consoleSender, i5)
                }
            }
        }.runTask(this)

    }

    fun blockPlace(slot: SlotInformation, key: String){

        object : BukkitRunnable(){
            override fun run() {
                val b = lightloc[key]!!.block
                b.type = slot.block
            }
        }.runTask(this)

    }

    class SlotInformation{

        var slot_name = ""
        var price: Double = 0.0

        var reel1 = mutableListOf<ItemStack>()
        var reel2 = mutableListOf<ItemStack>()
        var reel3 = mutableListOf<ItemStack>()

        var stock = 0.0

        var win = "0"
        var block: Material? = null

        var chancenum = 0

        val wining_name = HashMap<String, String>()
        val wining_item = HashMap<String, MutableList<ItemStack>>()
        val wining_prize = HashMap<String, Double>()
        val wining_stockodds = HashMap<String, Double>()
        val wining_chance = HashMap<String, MutableList<Double>>()
        val wining_step = HashMap<String, Int>()
        val wining_command = HashMap<String, MutableList<String>?>()
        val wining_con = HashMap<String, Boolean>()
        val wining_light = HashMap<String, Boolean>()
        val wining_lightsound = HashMap<String, Sound?>()
        val wining_sound = HashMap<String, Sound>()
        val win_particle = HashMap<String, Particle?>()
        val light_particle = HashMap<String, Particle?>()
        val stopsound1 = HashMap<String, Sound?>()
        val stopsound2 = HashMap<String, Sound?>()
        val stopsound3 = HashMap<String, Sound?>()
        val chancewin = HashMap<String, MutableList<String>?>()
        var countgame = 0
        var changegame = HashMap<String, Int>()

        var chancechange = HashMap<String, Int?>()

        var spin1 = false
        var spin2 = false
        var spin3 = false

        var allstop = true

        var p: Player? = null

        var pool: MoneyPoolObject? = null

        var spincount = 0
        val wincount = HashMap<String, Int>()

        var stopeffect: Effect? = null
        var changeeffect: Effect? = null
        var spineffect: Effect? = null

    }

    class Sound{
        var sound: org.bukkit.Sound? = null
        var volume = 0f
        var pitch = 0f
    }
    class Particle{
        var par: org.bukkit.Particle? = null
        var count: Int? = null
        var chance: Double? = null
    }

    class Effect{
        var particle: Particle? = null
        var sound: Sound? = null
        var command: MutableList<String>? = null
    }
}
