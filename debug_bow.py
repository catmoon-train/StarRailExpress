from PIL import Image, ImageDraw
DL = r"C:\Users\hai'man\Downloads"

def load_stage(path):
    img = Image.open(path)
    if img.mode == 'P':
        img = img.convert('RGBA')
    img = img.resize((16,16), Image.NEAREST)
    return img

# Load all 3 stages
base = load_stage(DL + r"\Bow_JE2_BE1.png")
p0   = load_stage(DL + r"\Bow_(Pull_0)_JE1_BE1.webp")
p1   = load_stage(DL + r"\Bow_(Pull_1)_JE1_BE1.webp")

# Create mask: B=body, G=grip, S=string
for name, img in [("base", base), ("pull_0", p0), ("pull_1", p1)]:
    px = img.load()
    print(f'\n--- {name} mask ---')
    mask = []
    for y in range(16):
        row = ''
        mr = ''
        for x in range(16):
            r,g,b,a = px[x,y]
            if a > 50:
                bri = (r+g+b)/3
                if bri > 140: 
                    row += 'S'
                    mr += '\"S\",'
                elif g > r and g > b: 
                    row += 'G'
                    mr += '\"G\",'
                else: 
                    row += 'B'
                    mr += '\"B\",'
            else:
                row += '-'
                mr += '\"-\",'
        mask.append(mr)
        print(row)
