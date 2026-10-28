import { describe, expect, it } from 'vitest'
import { cleanPhonetic, formatPhoneticDisplay } from '../../public/src/shared/vocabulary.js'

describe('Wordbook Phonetic Display Formatting', () => {
  it('cleanPhonetic strips redundant slashes', () => {
    expect(cleanPhonetic('/ˈæksɪdənt/')).toBe('ˈæksɪdənt')
    expect(cleanPhonetic('//ˈæksɪdənt//')).toBe('ˈæksɪdənt')
    expect(cleanPhonetic('ˈæksɪdənt')).toBe('ˈæksɪdənt')
    expect(cleanPhonetic('')).toBe('')
    expect(cleanPhonetic(null)).toBe('')
  })

  it('formats UK phonetic without double slashes when parsed is an object', () => {
    const parsed = {
      phonetic: { uk: '/ˈæksɪdənt/', us: '/ˈæksɪdənt/' },
    }
    expect(formatPhoneticDisplay(parsed, null)).toBe('UK /ˈæksɪdənt/')
  })

  it('formats UK phonetic when parsed is a JSON string', () => {
    const parsedStr = JSON.stringify({
      phonetic: { uk: '/ˈæksɪdənt/', us: '/ˈæksɪdənt/' },
    })
    expect(formatPhoneticDisplay(parsedStr, null)).toBe('UK /ˈæksɪdənt/')
  })

  it('formats dot-key phonetic like phonetic.uk', () => {
    const parsed = {
      'phonetic.uk': '/blɑːst/',
      'phonetic.us': '/blæst/',
    }
    expect(formatPhoneticDisplay(parsed, null)).toBe('UK /blɑːst/')
  })

  it('formats US phonetic when UK is not present', () => {
    const parsed = {
      phonetic: { us: '/blæst/' },
    }
    expect(formatPhoneticDisplay(parsed, null)).toBe('US /blæst/')
  })

  it('formats string phonetic in parsed (e.g. from basic catalog import)', () => {
    const parsed = {
      phonetic: '[ˈæksɪdənt]',
    }
    expect(formatPhoneticDisplay(parsed, null)).toBe('[ˈæksɪdənt]')
  })

  it('falls back to entry.phonetic when parsed has no phonetic', () => {
    expect(formatPhoneticDisplay({}, '/ˈæksent/')).toBe('/ˈæksent/')
    expect(formatPhoneticDisplay(null, '[ˈæksɪdənt]')).toBe('[ˈæksɪdənt]')
    expect(formatPhoneticDisplay(null, 'ˈæksent')).toBe('/ˈæksent/')
  })

  it('returns dash when no phonetic is available anywhere', () => {
    expect(formatPhoneticDisplay({}, null)).toBe('-')
    expect(formatPhoneticDisplay(null, '')).toBe('-')
    expect(formatPhoneticDisplay('invalid json', null)).toBe('-')
  })
})
