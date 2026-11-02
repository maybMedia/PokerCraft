"""Generate PokerCraft's 16px action items and a review sheet. Requires Pillow."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/pokercraft"
GOLD, LIGHT, INK = "#e9d99a", "#f4ead2", "#26332e"
NAMES = {"fold": "Fold", "call": "Check / Call", "raise": "Raise", "minus": "Lower Raise",
         "plus": "Increase Raise", "allin": "All-in", "join": "Join Table", "buyin": "Buy In",
         "admin": "Host Controls", "info": "Table Overview", "leave": "Leave / Cash Out",
         "show": "Show Hand", "muck": "Muck Hand"}


def icon(action):
    im = Image.new("RGBA", (16, 16))
    d = ImageDraw.Draw(im)
    if action == "show":
        d.rounded_rectangle((1, 2, 8, 13), radius=1, fill=LIGHT, outline=GOLD)
        d.rounded_rectangle((7, 1, 14, 12), radius=1, fill=LIGHT, outline=GOLD)
        d.polygon(((4, 5), (2, 8), (4, 10), (6, 8)), fill="#8d3548")
        d.polygon(((10, 4), (8, 7), (12, 7)), fill=INK)
        d.rectangle((10, 6, 10, 9), fill=INK)
        d.line((2, 15, 13, 15), fill=GOLD, width=1)
    elif action in ("fold", "muck"):
        d.rounded_rectangle((2, 1, 10, 13), radius=1, fill=LIGHT, outline=INK)
        d.rounded_rectangle((6, 3, 14, 15), radius=1, fill="#8d3548", outline=GOLD)
        d.line((8, 6, 12, 12), fill=LIGHT, width=2)
        d.line((12, 6, 8, 12), fill=LIGHT, width=2)
    elif action == "call":
        d.ellipse((1, 1, 14, 14), fill="#366c56", outline=GOLD, width=2)
        d.line((4, 8, 7, 11, 12, 5), fill=LIGHT, width=2)
    elif action in ("raise", "minus", "plus"):
        d.rounded_rectangle((1, 1, 14, 14), radius=3, fill="#315674", outline=GOLD)
        if action == "raise":
            d.polygon(((8, 3), (3, 8), (6, 8), (6, 12), (10, 12), (10, 8), (13, 8)), fill=LIGHT)
        else:
            d.rectangle((4, 7, 11, 8), fill=LIGHT)
            if action == "plus": d.rectangle((7, 4, 8, 11), fill=LIGHT)
    elif action in ("allin", "buyin"):
        for x, y, color in ((2, 8, "#366c56"), (7, 6, "#315674"), (4, 2, "#8d3548")):
            d.rectangle((x, y + 2, x + 7, y + 5), fill=color, outline=GOLD)
            d.ellipse((x, y, x + 7, y + 4), fill=color, outline=LIGHT)
        if action == "allin":
            d.polygon(((12, 0), (9, 5), (12, 5), (10, 9), (15, 3), (12, 3)), fill="#fff0a8")
        else:
            d.line((11, 9, 11, 14), fill=LIGHT, width=2)
            d.polygon(((8, 12), (14, 12), (11, 15)), fill=LIGHT)
    elif action == "join":
        d.ellipse((5, 1, 10, 6), fill=LIGHT)
        d.rounded_rectangle((3, 8, 12, 14), radius=2, fill="#366c56", outline=GOLD)
        d.line((11, 5, 15, 5), fill=GOLD, width=1)
        d.line((13, 3, 13, 7), fill=GOLD, width=1)
    elif action == "admin":
        d.polygon(((1, 4), (5, 7), (8, 2), (11, 7), (14, 4), (12, 12), (3, 12)), fill=GOLD, outline=INK)
        d.rectangle((3, 13, 12, 14), fill=LIGHT)
        d.rectangle((7, 8, 8, 10), fill="#8d3548")
    elif action == "info":
        d.ellipse((1, 1, 14, 14), fill="#315674", outline=GOLD)
        d.rectangle((7, 3, 8, 4), fill=LIGHT)
        d.rectangle((7, 7, 8, 11), fill=LIGHT)
        d.rectangle((6, 11, 9, 12), fill=LIGHT)
    else:
        d.rectangle((2, 1, 10, 14), fill="#5c4540", outline=GOLD)
        d.rectangle((4, 3, 8, 13), fill=INK)
        d.line((7, 8, 14, 8), fill=LIGHT, width=2)
        d.polygon(((11, 4), (15, 8), (11, 12)), fill=LIGHT)
    return im


def main():
    preview = Image.new("RGBA", (len(NAMES) * 96, 114), "#17271f")
    draw = ImageDraw.Draw(preview)
    lang_path = ASSETS / "lang/en_us.json"
    lang = json.loads(lang_path.read_text(encoding="utf-8"))
    for index, (action, name) in enumerate(NAMES.items()):
        im = icon(action)
        im.save(ASSETS / f"textures/item/action_{action}.png")
        model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"pokercraft:item/action_{action}"}}
        (ASSETS / f"models/item/action_{action}.json").write_text(json.dumps(model, indent=2) + "\n")
        lang[f"item.pokercraft.action_{action}"] = name
        preview.paste(im.resize((64, 64), Image.Resampling.NEAREST), (index * 96 + 16, 10))
        draw.text((index * 96 + 5, 86), name, fill=LIGHT)
    lang["key.pokercraft.menu"] = "Open Poker Host Controls"
    lang["category.pokercraft"] = "PokerCraft"
    lang["message.pokercraft.seated"] = "Poker controls: select a hotbar action, then right-click. Sneak to stand. P: host controls."
    lang_path.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    target = ROOT / "build/previews/action-icons.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    preview.save(target)


if __name__ == "__main__":
    main()
