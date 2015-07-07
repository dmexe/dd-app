class Integer
  def to_signed(bits)
    mask = (1 << (bits - 1))
    (self & ~mask) - (self & mask)
  end
end
