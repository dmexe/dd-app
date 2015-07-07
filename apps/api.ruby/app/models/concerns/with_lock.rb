require 'zlib'

module WithLock
  extend ActiveSupport::Concern

  def with_lock(options = {})
    self.class.with_lock(self.id, options) { yield }
  end

  class_methods do
    def with_lock(id, options = {})

      a = Zlib.crc32(self.name.to_s + options[:scope].to_s).to_signed(32)
      b = Zlib.crc32(id).to_signed(32)

      transaction do
        connection.execute "select pg_advisory_xact_lock(#{a}::int, #{b}::int)"
        yield
      end
    end
  end
end
