"""Generate detailed, UV-compatible 256px card atlases. Requires Pillow >= 10.1."""

from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/pokercraft"
TEXTURES = ASSETS / "textures/item/cards"
MODELS = ASSETS / "models/item/cards"
TEMPLATE = ROOT / "art/playing_card_template.png"
PREVIEW = ROOT / "build/previews/cards.png"
SCALE, AA = 4, 2
FACE_WIDTH, FACE_HEIGHT = 96, 144
PAPER = "#f8f3e7"
BORDER = "#cfc6b2"
INK, RED, GOLD = "#151a22", "#bc1834", "#b0802c"
SUITS = ("spades", "hearts", "diamonds", "clubs")
RANKS = [("ace", "A"), *[(str(n), str(n)) for n in range(2, 11)],
         ("jack", "J"), ("queen", "Q"), ("king", "K")]
PIPS = {
    2: [(48, 38), (48, 106)],
    3: [(48, 38), (48, 72), (48, 106)],
    4: [(x, y) for y in (38, 106) for x in (34, 62)],
    5: [(x, y) for y in (38, 106) for x in (34, 62)] + [(48, 72)],
    6: [(x, y) for y in (38, 72, 106) for x in (34, 62)],
    7: [(x, y) for y in (36, 72, 108) for x in (33, 63)] + [(48, 54)],
    8: [(x, y) for y in (36, 72, 108) for x in (33, 63)] + [(48, 54), (48, 90)],
    9: [(x, y) for y in (36, 60, 84, 108) for x in (33, 63)] + [(48, 72)],
    10: [(x, y) for y in (36, 60, 84, 108) for x in (33, 63)] + [(48, 48), (48, 96)],
}


class Canvas:
    """Draw source geometry at twice the atlas resolution for clean curved edges."""
    def __init__(self, width, height):
        self.image = Image.new("RGBA", (width * AA, height * AA))
        self.draw = ImageDraw.Draw(self.image)

    @staticmethod
    def box(bounds):
        return tuple(round(value * AA) for value in bounds)

    def rectangle(self, bounds, fill=None, outline=None, width=1):
        self.draw.rectangle(self.box(bounds), fill=fill, outline=outline, width=round(width * AA))

    def rounded(self, bounds, radius, fill=None, outline=None, width=1):
        self.draw.rounded_rectangle(self.box(bounds), round(radius * AA), fill=fill, outline=outline, width=round(width * AA))

    def ellipse(self, bounds, fill, outline=None, width=1):
        self.draw.ellipse(self.box(bounds), fill=fill, outline=outline, width=round(width * AA))

    def polygon(self, points, fill):
        self.draw.polygon([(round(x * AA), round(y * AA)) for x, y in points], fill=fill)

    def line(self, points, fill, width=1):
        self.draw.line([(round(x * AA), round(y * AA)) for x, y in points], fill=fill, width=round(width * AA))

    def text(self, position, text, size, fill):
        # Pillow's bundled scalable font makes regeneration independent of OS fonts.
        font = ImageFont.load_default(size=round(size * AA))
        x, y = position
        top = font.getbbox(text)[1]
        self.draw.text((round(x * AA), round(y * AA) - top), text, fill=fill, font=font, stroke_width=1)

    def composite(self, image, position):
        self.image.alpha_composite(image, self.box(position))

    def finish(self):
        return self.image.resize((self.image.width // AA, self.image.height // AA), Image.Resampling.LANCZOS)


def bezier(start, segments):
    points = [start]
    for a, b, end in segments:
        for step in range(1, 25):
            t = step / 24
            points.append(tuple((1 - t) ** 3 * start[i] + 3 * (1 - t) ** 2 * t * a[i]
                                + 3 * (1 - t) * t ** 2 * b[i] + t ** 3 * end[i] for i in (0, 1)))
        start = end
    return points


HEART = bezier((.5, .95), [
    ((.36, .81), (.02, .57), (.02, .32)),
    ((.02, .02), (.32, -.04), (.5, .21)),
    ((.68, -.04), (.98, .02), (.98, .32)),
    ((.98, .57), (.64, .81), (.5, .95)),
])


def suit(canvas, name, bounds, color, inverted=False):
    x, y, w, h = bounds

    def points(shape):
        if inverted:
            shape = [(1 - px, 1 - py) for px, py in shape]
        return [(x + px * w, y + py * h) for px, py in shape]

    def polygon(shape):
        canvas.polygon(points(shape), color)

    def circle(box):
        a, b, c, d = box
        if inverted:
            a, b, c, d = 1 - c, 1 - d, 1 - a, 1 - b
        canvas.ellipse((x + a * w, y + b * h, x + c * w, y + d * h), color)

    stem = [(.45, .50), (.55, .50), (.60, .84), (.75, .98), (.25, .98), (.40, .84)]
    if name == "hearts":
        polygon(HEART)
    elif name == "diamonds":
        polygon([(.5, .01), (.91, .5), (.5, .99), (.09, .5)])
    elif name == "spades":
        polygon([(px, (1 - py) * .80) for px, py in HEART])
        polygon(stem)
    else:
        polygon(stem)
        circle((.29, .01, .71, .43))
        circle((.01, .29, .47, .75))
        circle((.53, .29, .99, .75))
        polygon([(.35, .34), (.65, .34), (.65, .66), (.35, .66)])


def indices(face, label, name, color):
    corner = Canvas(28, 52)
    corner.text((1, 1), label, 28 if len(label) == 1 else 22, color)
    suit(corner, name, (0, 25, 20, 24), color)
    face.composite(corner.image, (5, 5))
    face.composite(corner.image.rotate(180), (FACE_WIDTH - 5 - 28, FACE_HEIGHT - 5 - 52))


def court(face, rank, name, color):
    half = Canvas(40, 42)
    half.rectangle((0, 0, 39, 41), fill="#eee3c7", outline=GOLD, width=1.5)
    hair = "#70442b" if rank == "Q" else "#30323b"
    if rank == "Q":
        half.ellipse((8, 11, 31, 36), hair)
    else:
        half.ellipse((10, 12, 29, 27), hair)
    half.ellipse((13, 14, 27, 29), "#e4b98c", outline="#9e6542")
    half.line([(16, 20), (18, 20)], INK)
    half.line([(23, 20), (25, 20)], INK)
    half.line([(21, 21), (20, 25), (23, 25)], "#b67e54")
    if rank == "K":
        half.polygon([(12, 23), (16, 25), (20, 26), (25, 24), (28, 23), (26, 30), (20, 34), (14, 30)], hair)
    else:
        half.line([(18, 27), (23, 27)], RED)
    half.polygon([(3, 41), (8, 32), (15, 29), (25, 29), (32, 32), (37, 41)], color)
    half.line([(9, 33), (30, 41)], GOLD, 2)
    if rank == "J":
        half.polygon([(8, 15), (10, 7), (25, 6), (31, 12), (29, 15)], "#315779")
        half.line([(9, 15), (30, 15)], GOLD, 2)
        half.polygon([(27, 10), (29, 1), (33, 0), (32, 6)], PAPER)
    else:
        crown = [(7, 5), (13, 10), (20, 1), (27, 10), (33, 5), (29, 15), (11, 15)]
        half.polygon(crown, GOLD)
        half.rectangle((12, 13, 28, 15), fill="#e8c66c")
        for cx in (13, 20, 27):
            half.ellipse((cx - 1, 10, cx + 1, 12), RED if rank == "K" else "#315779")
    suit(half, name, (16, 31, 8, 9), PAPER)
    face.composite(half.image, (28, 30))
    face.composite(half.image.rotate(180), (28, 72))


def joker(face, color):
    face.text((6, 5), "J", 24, color)
    hat = [(25, 55), (20, 34), (37, 47), (47, 27), (58, 47), (76, 34), (70, 55)]
    face.polygon(hat, color)
    face.line([(27, 55), (68, 55)], GOLD, 3)
    for x, y in ((20, 33), (47, 26), (76, 33)):
        face.ellipse((x - 3, y - 3, x + 3, y + 3), GOLD)
    face.ellipse((32, 55, 63, 87), "#e4b98c", outline=GOLD)
    for x in (41, 54):
        face.ellipse((x - 2, 64, x + 1, 68), INK)
    face.ellipse((45, 69, 51, 75), RED)
    face.line([(39, 78), (44, 82), (51, 82), (57, 78)], color, 2)
    face.polygon([(28, 87), (38, 92), (47, 86), (57, 92), (68, 87), (62, 101), (34, 101)], GOLD)
    face.polygon([(34, 100), (62, 100), (72, 114), (24, 114)], color)
    face.text((25, 122), "JOKER", 13, color)


def face_art(label, name, color):
    face = Canvas(FACE_WIDTH, FACE_HEIGHT)
    face.rounded((0, 0, 95.5, 143.5), 5, fill=PAPER, outline=BORDER, width=3)
    if label == "JOKER":
        joker(face, color)
    else:
        indices(face, label, name, color)
        if label == "A":
            suit(face, name, (26, 47, 44, 51), color)
        elif label in ("J", "Q", "K"):
            court(face, label, name, color)
        else:
            rank = int(label)
            w, h = (24, 28) if rank <= 3 else (16, 21) if rank <= 6 else (14, 16)
            for x, y in PIPS[rank]:
                suit(face, name, (x - w / 2, y - h / 2, w, h), color, inverted=y > 72)
    return face.finish()


def generate():
    # A fixed source template keeps regeneration repeatable after replacing the game assets.
    template = Image.open(TEMPLATE).convert("RGBA")
    assert template.size == (64, 64)
    atlas = template.resize((256, 256), Image.Resampling.NEAREST)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)
    cards = []
    for name in SUITS:
        color = RED if name in ("hearts", "diamonds") else INK
        for rank, label in RANKS:
            image = atlas.copy()
            image.paste(face_art(label, name, color), (0, 0))
            cards.append((f"{rank}_of_{name}", image))
    for name, color in (("red_joker", RED), ("black_joker", INK)):
        image = atlas.copy()
        image.paste(face_art("JOKER", None, color), (0, 0))
        cards.append((name, image))

    for name, image in cards:
        assert image.size == (256, 256)
        # Back, edge strips and unused UV regions retain the template's pixel-exact design.
        assert image.crop((96, 0, 256, 256)).tobytes() == atlas.crop((96, 0, 256, 256)).tobytes()
        assert image.crop((0, 144, 96, 256)).tobytes() == atlas.crop((0, 144, 96, 256)).tobytes()
        target = ASSETS / "textures/item/playing_card.png" if name == "ace_of_spades" else TEXTURES / f"{name}.png"
        image.save(target, optimize=True)
        texture = "pokercraft:item/playing_card" if name == "ace_of_spades" else f"pokercraft:item/cards/{name}"
        model = {"parent": "pokercraft:item/playing_card_base", "textures": {"0": texture, "particle": texture}}
        (MODELS / f"{name}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")

    selector = {"parent": "pokercraft:item/playing_card_base", "overrides": [
        {"predicate": {"custom_model_data": face}, "model": f"pokercraft:item/cards/{name}"}
        for face, (name, _) in enumerate(cards, 1)]}
    (ASSETS / "models/item/playing_card.json").write_text(json.dumps(selector, indent=2) + "\n", encoding="utf-8")
    for filename in ("playing_card_base.json", "card_back.json"):
        path = ASSETS / "models/item" / filename
        model = json.loads(path.read_text(encoding="utf-8"))
        model["texture_size"] = [256, 256]
        path.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")

    preview = Image.new("RGB", (13 * 112 + 24, 5 * 172 + 24), "#202a28")
    draw = ImageDraw.Draw(preview)
    for n, (name, image) in enumerate(cards):
        x, y = 12 + (n % 13) * 112, 12 + (n // 13) * 172
        face = image.crop((0, 0, FACE_WIDTH, FACE_HEIGHT))
        preview.paste(face, (x, y), face)
        draw.text((x, y + 148), name.replace("_of_", " ").replace("_", " "), fill=PAPER)
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    preview.save(PREVIEW)
    assert len(cards) == 54
    assert len({image.tobytes() for _, image in cards}) == 54, "Duplicate card artwork"
    for rank, positions in PIPS.items():
        assert len(positions) == rank
    print(f"Generated 54 detailed 256px atlases and matching models; preview: {PREVIEW}")


if __name__ == "__main__":
    generate()
