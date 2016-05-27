local args = {...}
local component = require("component")
local event = require("event")
local gpu = component.gpu
local unicode = require("unicode")
local keyboard = require("keyboard")
local text = require("text")
local os = require("os")
local pal = {}

local q = {}
for i=0,255 do
  local dat = (i & 0x01) << 7
  dat = dat | (i & 0x02) >> 1 << 6
  dat = dat | (i & 0x04) >> 2 << 5
  dat = dat | (i & 0x08) >> 3 << 2
  dat = dat | (i & 0x10) >> 4 << 4
  dat = dat | (i & 0x20) >> 5 << 1
  dat = dat | (i & 0x40) >> 6 << 3
  dat = dat | (i & 0x80) >> 7
  q[i + 1] = 0x2800 | dat
end

function error(str)
  print("ERROR: " .. str)
  os.exit()
end

function resetPalette(data)
 for i=0,255 do
  if (i < 16) then
    if data == nil then
      pal[i] = (i * 15) << 16 | (i * 15) << 8 | (i * 15)
    else
      pal[i] = data[3][i]
      gpu.setPaletteColor(i, data[3][i])
    end
  else
    local j = i - 16
    local b = math.floor((j % 5) * 255 / 4.0)
    local g = math.floor((math.floor(j / 5.0) % 8) * 255 / 7.0)
    local r = math.floor((math.floor(j / 40.0) % 6) * 255 / 5.0)
    pal[i] = r << 16 | g << 8 | b
  end
 end
end

resetPalette(nil)

function r8(file)
  local byte = file:read(1)
  if byte == nil then
    return 0
  else
    return string.byte(byte) & 255
  end
end

function r16(file)
  local x = r8(file)
  return x | (r8(file) << 8)
end

function loadImage(filename)
  local data = {}
  local file = io.open(filename, 'rb')
  local hdr = {67,84,73,70}

  for i=1,4 do
    if r8(file) ~= hdr[i] then
      error("Invalid header!")
    end
  end

  local hdrVersion = r8(file)
  local platformVariant = r8(file)
  local platformId = r16(file)

  if hdrVersion > 1 then
    error("Unknown header version: " .. hdrVersion)
  end

  if platformId ~= 1 or platformVariant ~= 0 then
    error("Unsupported platform ID: " .. platformId .. ":" .. platformVariant)
  end

  data[1] = {}
  data[2] = {}
  data[3] = {}
  data[2][1] = r8(file)
  data[2][1] = (data[2][1] | (r8(file) << 8))
  data[2][2] = r8(file)
  data[2][2] = (data[2][2] | (r8(file) << 8))

  local pw = r8(file)
  local ph = r8(file)
  if not (pw == 2 and ph == 4) then
    error("Unsupported character width: " .. pw .. "x" .. ph)
  end

  data[2][3] = r8(file)
  if (data[2][3] ~= 4 and data[2][3] ~= 8) or data[2][3] > gpu.getDepth() then
    error("Unsupported bit depth: " .. data[2][3])
  end

  local ccEntrySize = r8(file)
  local customColors = r16(file)
  if customColors > 0 and ccEntrySize ~= 3 then
    error("Unsupported palette entry size: " .. ccEntrySize)
  end
  if customColors > 16 then
    error("Unsupported palette entry amount: " .. customColors)
  end

  for p=0,customColors-1 do
    local w = r16(file)
    data[3][p] = w | (r8(file) << 16)
  end

  local WIDTH = data[2][1]
  local HEIGHT = data[2][2]

  for y=0,HEIGHT-1 do
    for x=0,WIDTH-1 do
      local j = (y * WIDTH) + x + 1
      local w = r16(file)
      if data[2][3] > 4 then
        data[1][j] = w | (r8(file) << 16)
      else
        data[1][j] = w
      end
    end
  end

  io.close(file)
  return data
end

function gpuBG()
  local a, al = gpu.getBackground()
  if al then
    return gpu.getPaletteColor(a)
  else
    return a
  end
end
function gpuFG()
  local a, al = gpu.getForeground()
  if al then
    return gpu.getPaletteColor(a)
  else
    return a
  end
end

function drawImage(data, offx, offy)
  if offx == nil then offx = 0 end
  if offy == nil then offy = 0 end

  local WIDTH = data[2][1]
  local HEIGHT = data[2][2]

  gpu.setResolution(WIDTH, HEIGHT)
  resetPalette(data)

  local bg = 0
  local fg = 0
  local cw = 0

  for y=0,HEIGHT-1 do
    local str = ""
    for x=0,WIDTH-1 do
      local ind = (y * WIDTH) + x + 1
      local gBG = gpuBG()
      local gFG = gpuFG()
      if data[2][3] > 4 then
        bg = pal[data[1][ind] & 0xFF]
        fg = pal[(data[1][ind] >> 8) & 0xFF]
        cw = ((data[1][ind] >> 16) & 0xFF) + 1
      else
        fg = pal[data[1][ind] & 0x0F]
        bg = pal[(data[1][ind] >> 4) & 0x0F]
        cw = ((data[1][ind] >> 8) & 0xFF) + 1
      end
      if (gBG == fg) and (gFG == bg) then
        str = str .. unicode.char(q[cw ^ 255])
      elseif (gBG ~= bg) or (gFG ~= fg) then
        if #str > 0 then
          gpu.set(x + 1 + offx - unicode.wlen(str), y + 1 + offy, str)
        end
        if gBG ~= bg then
          gpu.setBackground(bg)
        end
        if gFG ~= fg then
          gpu.setForeground(fg)
        end
        str = unicode.char(q[cw])
      else
        str = str .. unicode.char(q[cw])
      end
    end
    if #str > 0 then
      gpu.set(WIDTH + 1 - unicode.wlen(str) + offx, y + 1 + offy, str)
    end
  end
end

local image = loadImage(args[1])
drawImage(image)

while true do
    local name,addr,char,key,player = event.pull("key_down")
    if key == 0x10 then
        break
    end
end

gpu.setBackground(0, false)
gpu.setForeground(16777215, false)
gpu.setResolution(80, 25)
gpu.fill(1, 1, 80, 25, " ")
