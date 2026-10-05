if redis.call('exists', KEYS[2]) == 1 then
    return 0
end
local fields = {'*'}
for i = 1, #ARGV do
    fields[#fields + 1] = ARGV[i]
end
local id = redis.call('xadd', KEYS[1], unpack(fields))
redis.call('set', KEYS[2], id, 'EX', 2592000)
return 1
