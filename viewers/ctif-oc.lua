local args = {...};
local ctif = require('ctif-oc-lib');

local image = ctif.loadImage(args[1]);
ctif.drawImage(image);

while true do
    local name,addr,char,key,player = event.pull("key_down");
    if key == 0x10 then
        break;
    end
end

gpu.setBackground(0, false);
gpu.setForeground(16777215, false);
gpu.setResolution(80, 25);
gpu.fill(1, 1, 80, 25, " ");
