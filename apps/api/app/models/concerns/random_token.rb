require 'securerandom'

module RandomToken

  def random_token
    SecureRandom.urlsafe_base64(48)[0..64]
  end

end
